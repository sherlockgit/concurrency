### ThreadPoolExecutor 构造函数

`8.2` 在书里不是一个独立的示例类，而是 `ThreadPoolExecutor` 的完整构造函数签名，用来说明线程池的几个关键配置项是如何组合在一起的。

```java
public ThreadPoolExecutor(int corePoolSize,
                          int maximumPoolSize,
                          long keepAliveTime,
                          TimeUnit unit,
                          BlockingQueue<Runnable> workQueue,
                          ThreadFactory threadFactory,
                          RejectedExecutionHandler handler) { ... }
```

#### 这 7 个参数分别控制什么

- `corePoolSize`
  线程池的核心线程数。即使暂时空闲，这部分线程通常也会被保留。

- `maximumPoolSize`
  线程池允许创建的最大线程数。

- `keepAliveTime`
  当线程数超过核心线程数时，多出来的空闲线程最多还能存活多久。

- `unit`
  `keepAliveTime` 的时间单位。

- `workQueue`
  任务队列，用来保存等待执行的任务。
  常见实现包括：
  `LinkedBlockingQueue`、`ArrayBlockingQueue`、`SynchronousQueue`。

- `threadFactory`
  线程工厂，决定线程该如何创建。
  可以用它统一线程名、守护线程属性、优先级、异常处理器等。

- `handler`
  拒绝策略。
  当线程池已经到达最大线程数，并且任务队列也无法再接收新任务时，就由它决定如何处理新提交的任务。

#### 这一段想说明什么

`ThreadPoolExecutor` 的行为，本质上就是由这几类配置共同决定的：

1. 线程数量边界：`corePoolSize`、`maximumPoolSize`
2. 空闲线程回收策略：`keepAliveTime`、`unit`
3. 任务缓存方式：`workQueue`
4. 线程创建方式：`threadFactory`
5. 超载时的处理方式：`handler`

也就是说，所谓“线程池策略”，并不是一个单独开关，而是这几项配置组合出来的结果。

#### 和 `SynchronousQueue` 的关系

如果 `workQueue` 传入的是 `SynchronousQueue`，那么线程池就不会真正缓存等待中的任务，而是更倾向于：

1. 直接把任务移交给空闲工作线程
2. 如果没有空闲线程，并且线程数还没到上限，就创建新线程
3. 如果也不能创建新线程，就触发拒绝策略

这也是为什么 `newCachedThreadPool` 会使用 `SynchronousQueue`。
