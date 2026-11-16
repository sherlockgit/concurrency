# BlockingQueue 如何保证 Happens-Before

## 问题

`BlockingQueue` 是如何保证 Happens-Before 的？符合了哪个 JMM 原则？

## 接口层面的保证

`BlockingQueue` 的内存一致性规则明确规定：

```text
某线程在将元素放入 BlockingQueue 之前的操作，
happens-before
另一线程访问或移除该元素之后的操作。
```

换句话说，队列不只是保存对象引用的容器，也是线程之间移交数据时的同步边界。

例如：

```java
BlockingQueue<Task> queue = new LinkedBlockingQueue<Task>();

// 线程 A：生产者
configVersion = 5;             // 普通写
task.setName("import");        // 普通写
queue.put(task);               // 交接 task

// 线程 B：消费者
Task received = queue.take();  // 取得同一个 task
System.out.println(configVersion);   // 必须看到 5
System.out.println(received.getName()); // 必须看到 import
```

这里即使 `configVersion` 不是 `volatile`，线程 B 在成功取得这个 `task` 后仍必须看到值 `5`。

## Happens-Before 链路

完整链路如下：

```text
线程 A：
configVersion = 5
    -> 同一线程内的程序顺序
task.setName("import")
    -> 同一线程内的程序顺序
queue.put(task)
    -> BlockingQueue 的内存一致性保证
queue.take() 返回同一个 task
    -> 同一线程内的程序顺序
线程 B 读取 configVersion 与 task.name
```

根据 Happens-Before 的传递性，可以得到：

```text
线程 A 在 put(task) 之前的写入
    happens-before
线程 B 在 take() 返回该 task 之后的读取。
```

## 符合哪些 JMM 规则

这个保证不是一条新的 JMM 基础规则，而是并发队列内部组合以下规则实现的：

1. **程序顺序规则**

   在线程 A 内，业务数据写入发生在 `put()` 之前；在线程 B 内，`take()` 返回发生在读取业务数据之前。

2. **锁规则或 volatile 变量规则**

   队列实现使用锁、`volatile`、CAS、原子变量等同步原语，建立生产者和消费者之间的同步关系。

3. **传递性规则**

   将“业务数据写入 -> 队列内部同步 -> 消费者读取”连接成一条完整 Happens-Before 链。

## ArrayBlockingQueue：依靠同一把锁

`ArrayBlockingQueue` 使用一把 `ReentrantLock` 保护数组、元素数量和读写下标。

它的关键流程可以简化为：

```text
线程 A：
写入业务数据
    ->
put() 获取 queue.lock
    ->
将 task 放入数组
    ->
释放 queue.lock

线程 B：
获取同一个 queue.lock
    ->
从数组取出 task
    ->
读取业务数据
```

`ReentrantLock` 的内存语义与内置锁相同：

```text
线程 A 对同一把锁的 unlock
    happens-before
线程 B 后续对该锁成功的 lock。
```

再加上程序顺序规则，就得到：

```text
线程 A 写入 task 的字段
    happens-before
线程 B 从队列取出 task 后读取这些字段。
```

### Condition 的作用

当队列为空时，消费者会在 `notEmpty.await()` 中等待；生产者入队后会调用 `notEmpty.signal()`。

要注意：

```text
signal() 的主要职责是通知“可以重新检查条件了”；
它不是单独负责数据可见性的机制。
```

被唤醒的消费者必须重新获得 `queue.lock`，而锁的释放和获取关系才提供了可见性保证。

## LinkedBlockingQueue：两把锁加原子计数桥接

`LinkedBlockingQueue` 为了让生产与消费尽量并行，使用两把锁：

```text
putLock  ：保护入队操作。
takeLock ：保护出队操作。
```

生产者和消费者通常不通过同一把锁交接数据，因此还需要一个原子计数器：

```java
private final AtomicInteger count = new AtomicInteger();
```

简化后的内部流程：

```text
线程 A：
写 task 的字段
    ->
在 putLock 保护下连接新节点
    ->
count.getAndIncrement()    // 原子更新，具有 volatile 读写语义

线程 B：
count.get()                // 原子读取，具有 volatile 读语义
    ->
在 takeLock 保护下取得节点和 task
```

其中 `count` 是可见性桥梁：

```text
生产者对 count 的原子更新
    happens-before
消费者后续对 count 的原子读取。
```

因此，生产者在连接节点、写入元素之前所做的写入，会被消费者看到。JDK 源码在读取 `count` 的位置也明确注释：`count.get` 为队列前面的节点提供可见性。

## 为什么不是“被唤醒就一定可见”

下面的理解不准确：

```text
生产者 signal 了消费者，消费者醒来，所以消费者能看到最新数据。
```

正确理解是：

```text
signal / await 解决的是“什么时候应该醒来并重新检查条件”；
锁、volatile、CAS、原子变量解决的是“醒来后能看到哪些写入”。
```

`ArrayBlockingQueue` 主要依靠同一把锁；`LinkedBlockingQueue` 依靠两把锁和原子 `count` 的 volatile 语义共同完成发布和获取。

## 使用时的边界

队列保证覆盖的是：

```text
线程 A 在 put(task) 之前的操作。
```

下面的后续修改不受这次移交的保护：

```java
queue.put(task);
task.setName("changed later"); // 无同步地继续修改
```

此时消费者和修改者之间又产生了新的数据竞争。消费者不保证看到 `"changed later"`，更不能把可变的 `task` 当成永久稳定的快照。

要让移交后的对象状态保持稳定，通常应：

- 传递不可变对象；
- 或由单个线程独占对象；
- 或在后续修改和读取之间继续建立正确同步。

## 一句话总结

```text
BlockingQueue 通过锁、volatile、CAS 等内部同步原语，
把“生产者在 put 前的写入”安全地发布给“消费者在取到同一元素后的读取”。
```

对调用方而言，应依赖 `BlockingQueue` 的内存一致性契约，而不要依赖某个具体实现恰好使用的是锁还是 CAS。

## 参考资料

- [BlockingQueue JDK API 文档](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/BlockingQueue.html)
- [ArrayBlockingQueue OpenJDK 源码](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/ArrayBlockingQueue.java)
- [LinkedBlockingQueue OpenJDK 源码](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/util/concurrent/LinkedBlockingQueue.java)
