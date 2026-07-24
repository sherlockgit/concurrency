# JMM 的 Happens-Before 关系

## 问题

说一下 JMM 的 Happens-Before 关系。

## 一句话理解

`Happens-Before` 可以通俗理解为：

```text
Java 内存模型规定的一套“前面的结果，后面必须看得见”的规则。
```

它不是单纯描述时间先后，而是描述：

```text
如果操作 A happens-before 操作 B，
那么 A 的执行结果必须对 B 可见，
并且 A 的执行顺序在语义上排在 B 前面。
```

## 为什么需要 Happens-Before

在单线程里，代码顺序通常很好理解：

```java
int a = 0;
a = 3;
System.out.println(a);
```

这个线程自己一定能看到 `a == 3`。

但多线程中，问题会复杂很多：

```java
int data = 0;
boolean ready = false;
```

线程 A：

```java
data = 3;
ready = true;
```

线程 B：

```java
if (ready) {
    System.out.println(data);
}
```

直觉上你可能认为：

```text
线程 B 只要看到 ready == true，
就应该看到 data == 3。
```

但如果没有同步，这个结论不成立。

线程 B 可能看到：

```text
ready == true
data == 0
```

原因可能是：

- `data = 3` 还没有对线程 B 可见；
- `ready = true` 先对线程 B 可见；
- 编译器或处理器做了重排序；
- 线程 B 从自己的处理器缓存中读到了旧值。

所以 JMM 需要一套规则说明：

```text
什么情况下，一个线程的写入必须被另一个线程看到。
```

这套规则就是 Happens-Before。

## Happens-Before 不是时间先后

`Happens-Before` 这个名字容易误导。

它不是说：

```text
A 在真实时间上一定先执行，B 在真实时间上一定后执行。
```

它真正强调的是：

```text
A 的结果对 B 可见。
```

例如：

```java
volatile boolean ready;
int data;
```

线程 A：

```java
data = 3;
ready = true;
```

线程 B：

```java
if (ready) {
    System.out.println(data);
}
```

如果线程 B 读到了 `ready == true`，那么：

```text
线程 A 写 ready = true
happens-before
线程 B 读 ready == true
```

再加上同一线程内的程序顺序：

```text
data = 3
happens-before
ready = true
```

通过传递性可以得到：

```text
data = 3
happens-before
线程 B 读取 data
```

因此线程 B 必须看到 `data == 3`。

## 常见 Happens-Before 规则

### 程序顺序规则

同一个线程中，前面的操作 happens-before 后面的操作。

例如：

```java
a = 1;
b = 2;
```

在当前线程内：

```text
a = 1 happens-before b = 2
```

注意，这不表示其他线程一定按照这个顺序看到它们。

如果想让其他线程也看到这个顺序，需要再配合同步规则。

### 锁规则

一个锁的 `unlock` happens-before 后续对同一把锁的 `lock`。

例如：

```java
synchronized (lock) {
    data = 3;
}
```

另一个线程：

```java
synchronized (lock) {
    System.out.println(data);
}
```

如果两个线程使用的是同一把锁，那么：

```text
线程 A 退出 synchronized
happens-before
线程 B 进入 synchronized
```

所以线程 B 能看到线程 A 在同步块内写入的 `data = 3`。

### volatile 规则

对一个 `volatile` 变量的写 happens-before 后续对同一个 `volatile` 变量的读。

例如：

```java
int data;
volatile boolean ready;
```

线程 A：

```java
data = 3;
ready = true;
```

线程 B：

```java
if (ready) {
    System.out.println(data);
}
```

只要线程 B 读到了 `ready == true`，它就必须看到线程 A 在写 `ready` 之前的普通写入：

```text
data = 3
```

### 线程启动规则

调用 `Thread.start()` 之前的操作 happens-before 新线程中的动作。

例如：

```java
data = 3;
thread.start();
```

新线程中：

```java
System.out.println(data);
```

新线程一定能看到 `start()` 之前已经写好的 `data = 3`。

### 线程终止规则

线程中的所有操作 happens-before 其他线程成功从 `join()` 返回。

例如：

```java
Thread worker = new Thread(new Runnable() {
    @Override
    public void run() {
        data = 3;
    }
});

worker.start();
worker.join();
System.out.println(data);
```

`join()` 返回后，主线程一定能看到 worker 线程中已经完成的写入。

### 中断规则

一个线程调用另一个线程的 `interrupt()`，happens-before 被中断线程检测到中断。

例如，被中断线程通过这些方式观察中断：

- `InterruptedException`；
- `Thread.interrupted()`；
- `Thread.currentThread().isInterrupted()`。

### 传递性规则

如果：

```text
A happens-before B
B happens-before C
```

那么：

```text
A happens-before C
```

这是 Happens-Before 非常重要的一条规则。

前面 `volatile ready` 的例子就是靠传递性串起来的：

```text
data = 3
happens-before
ready = true
happens-before
读到 ready == true
happens-before
读取 data
```

最终得到：

```text
data = 3 对读取 data 的线程可见
```

## 结合 16-1 的例子

项目中的 16-1：

[PossibleReordering.java](D:/project/concurrency/concurrency/src/main/java/com/sherlock/concurrency/chapter16/detailed_16_01/PossibleReordering.java)

里面有两个线程：

```java
// 线程 one
a = 1;
x = b;
```

```java
// 线程 other
b = 1;
y = a;
```

因为 `a`、`b`、`x`、`y` 都不是 `volatile`，也没有被同一把锁保护，所以两个线程之间没有建立可靠的 Happens-Before 关系。

因此理论上可能出现：

```text
x == 0
y == 0
```

这表示：

```text
线程 one 没看到线程 other 写入的 b = 1；
线程 other 也没看到线程 one 写入的 a = 1。
```

这不是说这两个写入一定没执行，而是说：

```text
没有 Happens-Before 关系时，另一个线程不保证能看到这些写入。
```

## 有 Happens-Before 和没有 Happens-Before 的区别

有 Happens-Before：

```text
前一个操作的结果，后一个操作必须能看到。
```

没有 Happens-Before：

```text
后一个操作可能看到最新值，也可能看到旧值。
不能写代码依赖“我觉得它应该看得到”。
```

## 常见同步工具和 Happens-Before

这些工具都可以建立可见性关系：

- `synchronized`；
- `ReentrantLock`；
- `volatile`；
- `Thread.start()`；
- `Thread.join()`；
- `CountDownLatch`；
- `Future.get()`；
- `BlockingQueue`；
- `AtomicInteger`、`AtomicReference` 等原子变量。

例如 `CountDownLatch`：

```java
data = 3;
latch.countDown();
```

另一个线程：

```java
latch.await();
System.out.println(data);
```

只要 `await()` 是因为 `countDown()` 之后计数归零而返回，那么 `countDown()` 之前的写入对 `await()` 返回后的线程可见。

## 一句话总结

```text
Happens-Before 是 JMM 判断“一个线程的写入是否必须对另一个线程可见”的规则。
```

再通俗一点：

```text
有 HB，就必须看得见；
没有 HB，就不要假设看得见。
```

