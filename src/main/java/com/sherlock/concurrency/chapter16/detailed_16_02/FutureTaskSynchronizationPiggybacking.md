# FutureTask 中的同步搭便车

## 一、这段话讲的是什么

书中所说的“借助现有同步机制的可见性属性”，也叫同步搭便车（synchronization piggybacking）。

它的意思是：某个普通变量本身没有使用 `volatile` 或锁，但它可以借助另一个已经具备同步语义的变量或操作，获得线程之间的可见性。

可以先记住这个模型：

```text
普通数据写入
    -> 发布同步信号
    -> 另一个线程观察到同步信号
    -> 读取普通数据
```

普通数据不是天然可见，而是借用了同步信号建立的 Happens-Before 关系。

---

## 二、一个简单例子

```java
private int data;                 // 普通变量
private volatile boolean ready;   // volatile 同步信号

// 线程 A
data = 42;
ready = true;

// 线程 B
while (!ready) {
}
System.out.println(data);
```

虽然 `data` 不是 `volatile`，但线程 B 读到 `ready == true` 后，必须看到 `data == 42`。

这是因为以下几条关系可以串起来：

```text
线程 A 内的程序顺序：
data = 42
    happens-before
ready = true

volatile 规则：
ready = true
    happens-before
线程 B 读取到 ready == true

线程 B 内的程序顺序：
读取 ready
    happens-before
读取 data
```

根据 Happens-Before 的传递性：

```text
写入 data = 42
    happens-before
线程 B 读取 data
```

因此，`data` 的可见性是借助 `ready` 的 `volatile` 写-读关系获得的，这就是“同步搭便车”。

---

## 三、FutureTask 中的对应关系

早期 JDK 的 `FutureTask` 内部使用 AQS 管理任务完成状态和等待线程。可以把其中的关键部分简化为：

```java
class Sync extends AbstractQueuedSynchronizer {
    private Object result; // 任务结果，可能不是 volatile
}
```

任务执行线程完成任务时，大致执行：

```java
public void set(Object value) {
    result = value;
    releaseShared(0);
}
```

调用者执行 `get()` 时，大致执行：

```java
public Object get() throws InterruptedException {
    acquireSharedInterruptibly(0);
    return result;
}
```

关键流程如下：

```text
任务线程                              get() 线程

result = value
    |
    v
releaseShared()
    |
    | 修改完成状态并唤醒等待线程
    v
                              acquireSharedInterruptibly()
                                      |
                                      v
                              return result
```

可以抽象成下面的 Happens-Before 链路：

```text
写入 result
    happens-before
releaseShared()
    happens-before
acquireSharedInterruptibly() 返回
    happens-before
读取 result
```

所以，即使 `result` 本身不是 `volatile`，调用 `get()` 的线程仍然可以安全地看到任务线程写入的结果。

---

## 四、AQS 在这里做了什么

AQS 在这个设计中主要负责两件事。

### 1. 任务未完成时让线程等待

如果任务还没有完成，`get()` 会调用：

```java
acquireSharedInterruptibly(0);
```

AQS 检查当前状态后发现任务未完成，就会让调用 `get()` 的线程进入等待队列，而不是一直占用 CPU 空转。

### 2. 任务完成时唤醒等待线程

任务执行线程完成任务后调用：

```java
releaseShared(0);
```

AQS 会根据同步器的实现：

1. 更新任务完成状态；
2. 唤醒等待任务完成的线程；
3. 让被唤醒的线程重新尝试获取共享状态；
4. 让获取成功的线程从 `get()` 返回。

因此，`releaseShared()` 不只是“通知等待线程”，同时也是任务结果发布过程中的同步边界。

---

## 五、为什么 `result` 不一定需要声明为 volatile

当然可以把结果写成：

```java
private volatile Object result;
```

这样可以解决 `result` 的可见性问题。

但是 `FutureTask` 不只需要发布结果，还需要处理：

- 任务是否完成；
- 多个线程同时调用 `get()`；
- 线程等待和唤醒；
- 任务取消；
- 任务异常；
- 等待过程中的中断；
- 任务只能执行一次。

AQS 已经提供了状态管理、等待队列和线程唤醒机制。因此可以让结果字段借助这套已有的同步机制，而不必为结果字段单独设计一套同步方案。

这就是书中所说的：

```text
虽然也可以把结果引用声明为 volatile，
但利用现有同步机制可以实现相同的可见性保证。
```

这里的重点不是“绝对不能使用 `volatile`”，而是复用已有同步路径，统一管理任务状态、等待、唤醒和结果发布。

---

## 六、为什么语句顺序非常重要

正确顺序是：

```java
result = value;
releaseShared(0);
```

必须先写入结果，再发布任务完成信号。

下面的顺序就不安全：

```java
releaseShared(0);
result = value;
```

因为等待线程可能在 `releaseShared()` 之后已经被唤醒，并从 `get()` 返回。此时 `result = value` 甚至还没有执行，调用者就可能读到旧值。

因此，同步搭便车必须满足：

```text
普通数据写入必须发生在同步信号发布之前；
读取线程必须先观察同步信号，再读取普通数据。
```

这就是书中说“该技术对语句顺序非常敏感，因此很容易出错”的原因。

---

## 七、与现代 JDK 源码的关系

书中描述的是早期 JDK 的 `FutureTask` 实现，当时 `FutureTask` 内部有一个继承自 `AbstractQueuedSynchronizer` 的 `Sync` 类。

在现代 JDK 中，`FutureTask` 的实现已经发生变化，通常可以看到：

- `volatile` 任务状态；
- `volatile` 的运行线程引用；
- 等待线程链表；
- CAS；
- `VarHandle` 或其他底层同步操作。

因此，在当前 JDK 源码里可能找不到书中所说的 AQS `Sync` 类。

但核心思想没有改变：

```text
任务线程写入结果
    -> 发布任务完成状态
    -> 唤醒等待线程
    -> get() 获取完成状态
    -> 读取任务结果
```

仍然需要通过同步状态建立 Happens-Before 关系，保证任务结果从执行线程安全地发布到调用 `get()` 的线程。

---

## 八、和“直接使用 volatile”的区别

两种方式的重点不同：

| 方式 | 主要解决的问题 |
| --- | --- |
| `volatile result` | 让结果字段的读写具有可见性，并限制相关重排序 |
| AQS 同步借用 | 同时管理任务状态、等待、唤醒、取消，并顺带保证结果可见 |

如果只是发布一个简单状态，使用 `volatile` 往往更直观。

如果还需要等待、唤醒、取消和异常传播，那么使用 AQS 或其他现有同步工具更合适。

---

## 九、一句话总结

```text
FutureTask 先把任务结果写入 result，
再通过 releaseShared 发布“任务已经完成”的同步信号；
get() 通过 acquireSharedInterruptibly 等待并获取这个完成信号，
然后读取 result。
```

`result` 的可见性不是来自它自身，而是借助了任务完成状态的同步过程。这就是同步搭便车（Piggybacking）。
