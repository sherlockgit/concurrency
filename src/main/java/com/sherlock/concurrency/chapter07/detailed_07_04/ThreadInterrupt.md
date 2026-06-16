### Thread 中断相关方法

`7.4` 在《Java 并发编程实战》官方代码清单中并不是一个独立的 Java 类，
而是一个外部链接，指向 `Thread` 类中与“中断”相关的方法说明。
因此，这里参考 `detailed_05_07/ConcurrentMap.md` 的做法，
将这一节整理成一份本地说明文档，便于和 `7.3`、`7.5` 放在一起学习。

#### 1. interrupt()

`interrupt()` 的作用不是“强制杀死线程”，而是向目标线程发送一个“中断请求”。
线程是否结束，取决于它是否正确地响应这个中断请求。

常见行为如下：

```java
thread.interrupt();
```

- 如果目标线程正在正常运行，那么它的“中断标志位”会被设置为 `true`。
- 如果目标线程阻塞在 `sleep`、`wait`、`join` 这类可中断阻塞方法上，
  那么这些方法通常会抛出 `InterruptedException`，并且在抛出异常前清除中断标志位。
- 如果目标线程阻塞在某些可中断的 I/O 或 NIO 操作上，
  JVM 也会按各自的规范唤醒或终止相关阻塞。

这一点正好解释了为什么 `detailed_07_03/BrokenPrimeProducer` 有问题：
仅靠一个 `volatile cancelled` 标志，无法唤醒已经阻塞在 `put()` 上的线程；
而 `detailed_07_05/PrimeProducer` 使用 `interrupt()` 后，就可以及时取消阻塞任务。

#### 2. isInterrupted()

`isInterrupted()` 是实例方法，用来检查“某个线程对象”的中断状态，
但**不会清除**这个状态。

```java
boolean interrupted = thread.isInterrupted();
```

它适合用于：

- 轮询判断某个线程是否已经收到取消请求。
- 在任务循环中作为退出条件。

例如：

```java
while (!Thread.currentThread().isInterrupted()) {
    // 执行业务逻辑
}
```

这种写法的含义是：只要当前线程还没有被中断，就继续工作；
一旦收到中断请求，就主动结束任务。

#### 3. interrupted()

`interrupted()` 是 `Thread` 的静态方法，它检查的是**当前线程**的中断状态，
并且**检查后会清除中断标志位**。

```java
boolean interrupted = Thread.interrupted();
```

这个方法最容易和 `isInterrupted()` 混淆，关键区别有两个：

- `interrupted()` 只能检查“当前线程”。
- `interrupted()` 调用后会清除中断标志位。

例如：

```java
Thread.currentThread().interrupt();

System.out.println(Thread.currentThread().isInterrupted()); // true
System.out.println(Thread.interrupted());                   // true
System.out.println(Thread.interrupted());                   // false
```

第一次 `Thread.interrupted()` 返回 `true` 的同时，也把中断状态清掉了，
所以第二次再调用时，结果就是 `false`。

#### 4. 为什么不能吞掉中断

当代码捕获到 `InterruptedException` 时，通常有两种合理做法：

- 继续向上抛出 `InterruptedException`，把“中断请求”交给上层调用者处理。
- 如果当前方法签名不能抛出该异常，就在清理后调用
  `Thread.currentThread().interrupt()` 恢复中断状态。

例如：

```java
try {
    Thread.sleep(1000);
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
}
```

如果只是简单 `catch` 住异常却什么都不做，
就相当于把外部线程发来的取消信号“吃掉”了，
这会让上层代码误以为线程从未被中断过。

#### 5. 与 7.3、7.5 的关系

- `7.3 BrokenPrimeProducer`：展示错误示例。线程可能永久卡在阻塞方法中，无法仅靠取消标志退出。
- `7.4 Thread interruption methods`：解释 `interrupt()`、`isInterrupted()`、`interrupted()` 的语义。
- `7.5 PrimeProducer`：展示正确示例。用中断来取消可能阻塞的生产者线程。

#### 6. 配套演示类

本目录下额外补了一个演示类：

```java
ThreadInterruptDemo
```

它不是书中的官方代码清单，而是为了帮助理解 `7.4` 补充的本地示例，
用于直观看到以下行为：

- `interrupt()` 如何打断阻塞中的线程。
- `InterruptedException` 抛出后中断状态会发生什么变化。
- `isInterrupted()` 和 `interrupted()` 的差异。
