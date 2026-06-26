### Executors.newFixedThreadPool

`8.3` 在书里不是一个独立的示例类，而是 `Executors` 中 `newFixedThreadPool` 工厂方法的代码片段，用来说明“固定大小线程池”在底层其实是如何由 `ThreadPoolExecutor` 组装出来的。

```java
public static ExecutorService newFixedThreadPool(int nThreads) {
    return new ThreadPoolExecutor(nThreads, nThreads,
                                  0L, TimeUnit.MILLISECONDS,
                                  new LinkedBlockingQueue<Runnable>());
}
```

#### 这个工厂方法表达了什么策略

- `corePoolSize = nThreads`
  核心线程数固定为 `nThreads`。

- `maximumPoolSize = nThreads`
  最大线程数也固定为 `nThreads`。

- `keepAliveTime = 0`
  由于线程池不会扩容到核心线程数以上，所以这个值在这里几乎没有实际意义。

- `workQueue = new LinkedBlockingQueue<Runnable>()`
  任务会先进入一个无界阻塞队列等待执行。

#### 这意味着什么

这个线程池的行为不是“忙了就继续加线程”，而是：

1. 先创建不超过 `nThreads` 个工作线程
2. 当这 `nThreads` 个线程都忙时，后续任务进入队列等待
3. 不会因为任务变多而继续创建更多线程

所以它的核心特征是：

- 线程数固定
- 吞吐依赖排队
- 更适合比较稳定、可控的并发度

#### 一个很关键的点

因为这里使用的是无界 `LinkedBlockingQueue`，所以一旦核心线程都忙了，任务会直接排队。

这会带来一个后果：

`maximumPoolSize` 在这种配置下实际上不会再起作用。

也就是说，虽然底层还是 `ThreadPoolExecutor`，但由于队列策略是“无界排队”，这个线程池从行为上就变成了“固定线程数 + 无限排队”。

#### 和你前面问到的 `SynchronousQueue` 的区别

`newFixedThreadPool`：

- 用 `LinkedBlockingQueue`
- 任务先排队
- 不倾向于扩线程

`newCachedThreadPool`：

- 用 `SynchronousQueue`
- 任务不排队，直接移交
- 更倾向于按需创建新线程

所以 `8.3` 这段代码，和你前面问到的那段 `SynchronousQueue`，其实正好是在对比两种不同的线程池策略。
