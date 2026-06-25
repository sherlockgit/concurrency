### Thread.UncaughtExceptionHandler

`7.24` 在官方清单里不是独立源码，而是 `Thread.UncaughtExceptionHandler` 的 Javadoc 入口。
这表示：当线程因为未捕获异常而异常终止时，JVM 会回调这个处理器。

#### 作用顺序

1. 线程自己设置的 `UncaughtExceptionHandler`
2. 线程所属 `ThreadGroup`
3. 全局默认 `UncaughtExceptionHandler`

#### 典型用法

```java
Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
    System.err.println("default handler: " + t.getName() + " -> " + e);
});

Thread thread = new Thread(() -> {
    throw new RuntimeException("boom");
}, "worker");

thread.setUncaughtExceptionHandler((t, e) -> {
    System.err.println("thread handler: " + t.getName() + " -> " + e);
});

thread.start();
```

#### 适合做什么

- 记录崩溃日志
- 输出线程名和异常堆栈
- 给监控系统上报故障

#### 不适合做什么

- 依赖它恢复业务状态
- 指望它替代正常的异常处理流程
- 在里面做复杂阻塞操作

