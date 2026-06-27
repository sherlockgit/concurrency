### 修改通过标准工厂创建的 Executor

`8.8` 在书里不是独立类，而是一个片段，主题是：

`Executors` 提供的标准工厂方法创建出来的执行器，有些可以强转后继续调底层实现的方法，有些不行。

#### 可以直接修改的情况

像这些工厂方法，JDK 直接返回具体实现类：

```java
ExecutorService fixed = Executors.newFixedThreadPool(2);
ExecutorService cached = Executors.newCachedThreadPool();
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(2);
```

它们在 `Executors.java` 里的返回值分别是：

- `newFixedThreadPool` -> `new ThreadPoolExecutor(...)`
- `newCachedThreadPool` -> `new ThreadPoolExecutor(...)`
- `newScheduledThreadPool` -> `new ScheduledThreadPoolExecutor(...)`

所以可以这样做：

```java
ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
```

#### 不能直接修改的情况

像这些工厂方法，JDK 返回的是包装器：

```java
ExecutorService single = Executors.newSingleThreadExecutor();
ScheduledExecutorService singleScheduled = Executors.newSingleThreadScheduledExecutor();
```

它们返回的是：

- `FinalizableDelegatedExecutorService`
- `DelegatedScheduledExecutorService`

这些包装器只暴露 `ExecutorService` / `ScheduledExecutorService` 接口方法，
故意不让你再向下转型成 `ThreadPoolExecutor` 去改配置。

这也是 JDK 文档里说的“guaranteed not to be reconfigurable”的含义。

#### 为什么要这样设计

- `newFixedThreadPool`、`newCachedThreadPool` 这类工厂更偏“便捷创建”，保留了底层可调性
- `newSingleThreadExecutor` 更强调“语义约束”：永远单线程顺序执行，不允许你后来偷偷改成多线程

#### 结论

如果你需要后续继续调线程池参数：

- 优先自己直接 `new ThreadPoolExecutor(...)`
- 或至少使用那些返回具体实现类的标准工厂方法

如果你需要的是“固定语义、不允许外部再调”：

- `newSingleThreadExecutor`
- `Executors.unconfigurableExecutorService(...)`

更合适

