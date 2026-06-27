### ThreadFactory 接口

`8.5` 在书里不是一个独立的业务示例，而是 `ThreadFactory` 接口本身。
它的作用是把“如何创建线程”这件事从业务逻辑里抽出来，统一交给线程工厂负责。

```java
public interface ThreadFactory {
    Thread newThread(Runnable r);
}
```

#### 它解决什么问题

如果代码里到处直接写：

```java
new Thread(runnable).start();
```

那么线程的这些属性就会散落在各处：

- 线程名
- 是否守护线程
- 优先级
- 所属线程组
- `UncaughtExceptionHandler`

`ThreadFactory` 的价值就在于把这些创建策略统一收口。

#### 常见用途

- 统一线程命名，方便排查问题
- 统一设置 `daemon`
- 统一设置优先级
- 为线程池中的所有 worker 统一设置 `UncaughtExceptionHandler`
- 统一切换为自定义 `Thread` 子类

#### Executors.defaultThreadFactory 做了什么

JDK 默认线程工厂会：

- 创建非守护线程
- 使用 `NORM_PRIORITY`
- 按 `pool-N-thread-M` 这种格式命名线程

这在 `Executors.java` 里可以直接看到。

#### 和线程池的关系

`ThreadPoolExecutor` 自己并不直接 `new Thread(...)`，
而是通过内部持有的 `ThreadFactory` 创建 worker 线程。

也就是说：

- 谁控制 `ThreadFactory`
- 谁就控制线程池里线程的创建方式

这也是为什么要给线程池统一设置 `UncaughtExceptionHandler` 时，
通常不是去“改线程”，而是给 `ThreadPoolExecutor` 提供自定义 `ThreadFactory`。

