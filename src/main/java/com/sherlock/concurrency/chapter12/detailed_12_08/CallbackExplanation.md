# 书中所说的“回调”是什么意思

## 问题

书中所说的“回调是什么意思”？

## 简单理解

回调就是：**你把一段代码交给别人，等别人运行到某个时机时，再反过来调用你这段代码。**

换句话说：

```text
普通调用：我主动调用别人。
回调：我把代码交给别人，别人到时候调用我。
```

## ThreadFactory 中的回调

以 12.8 的 `TestingThreadFactory` 为例：

```java
public class TestingThreadFactory implements ThreadFactory {
    public final AtomicInteger numCreated = new AtomicInteger();
    private final ThreadFactory factory = Executors.defaultThreadFactory();

    @Override
    public Thread newThread(Runnable runnable) {
        numCreated.incrementAndGet();
        return factory.newThread(runnable);
    }
}
```

测试代码会把这个 `ThreadFactory` 交给线程池：

```java
TestingThreadFactory threadFactory = new TestingThreadFactory();
ExecutorService pool = Executors.newFixedThreadPool(10, threadFactory);
```

这里有一个关键点：**测试代码并不直接调用 `threadFactory.newThread(...)`。**

真正调用它的是线程池。

当线程池内部发现自己需要创建新的工作线程时，它会回过头来调用：

```java
threadFactory.newThread(task);
```

所以 `ThreadFactory.newThread` 就是一个回调方法。

## Runnable 也是回调

再看一个更常见的例子：

```java
pool.execute(new Runnable() {
    @Override
    public void run() {
        System.out.println("任务开始执行");
    }
});
```

这里你只是把 `Runnable` 交给线程池。

你没有自己调用：

```java
runnable.run();
```

而是线程池中的某个工作线程，在合适的时候调用 `run()`。

所以 `Runnable.run()` 也可以看作一种回调。

## 为什么并发测试中回调很有用

并发对象内部很多行为，外部不容易直接观察。

例如线程池什么时候创建线程，测试代码通常看不到。因为创建线程这个动作发生在线程池内部。

但线程池创建线程时必须经过 `ThreadFactory`：

```text
线程池需要新工作线程
        ↓
调用 ThreadFactory.newThread(...)
        ↓
TestingThreadFactory.numCreated++
        ↓
测试代码可以断言创建线程数量
```

这样，回调就变成了一个“观察点”。

测试代码可以通过这个观察点判断线程池内部行为是否符合预期。

## 和书中那句话的关系

书中说：

> 在构造测试案例时，对客户提供的代码进行回调是非常有帮助的。

这里的“客户提供的代码”，就是调用者传给并发组件的代码，比如：

```text
传给线程池的 Runnable
传给线程池的 ThreadFactory
传给排序方法的 Comparator
传给 GUI 按钮的 Listener
```

这些代码由调用者提供，但由框架、线程池、容器或者其他组件在特定时机调用。

这就是回调。

## 一句话总结

回调不是“我现在调用你”，而是：

```text
我先把代码交给你；
等你运行到某个时机；
你再调用我这段代码。
```

在并发测试中，回调常用来观察对象生命周期中的关键位置，例如线程池创建线程、开始执行任务、任务执行完成等。
