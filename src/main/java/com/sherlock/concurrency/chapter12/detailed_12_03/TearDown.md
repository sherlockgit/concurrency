# tearDown

## 问题

什么是 `tearDown`？

`tearDown` 是测试框架里的测试后清理方法。

它的作用是：

```text
每个测试方法执行完之后，自动做清理工作。
```

## 执行顺序

在 JUnit 3 中，经常会看到：

```java
public class MyTest extends TestCase {

    @Override
    protected void setUp() {
        // 每个测试开始前执行
    }

    @Override
    protected void tearDown() {
        // 每个测试结束后执行
    }

    public void testSomething() {
        // 测试逻辑
    }
}
```

执行顺序是：

```text
setUp()
testSomething()
tearDown()
```

在 JUnit 4 中，对应的是：

```java
@Before
public void setUp() {
}

@After
public void tearDown() {
}
```

在 JUnit 5 中，对应的是：

```java
@BeforeEach
void setUp() {
}

@AfterEach
void tearDown() {
}
```

## tearDown 通常做什么

`tearDown` 常用于释放测试资源：

```text
关闭线程池；
停止后台线程；
关闭文件；
关闭 Socket；
关闭数据库连接；
清理临时文件；
重置共享状态；
检查子线程中是否有异常。
```

## 为什么并发测试特别需要 tearDown

并发测试经常会创建线程：

```java
Thread thread = new Thread(task);
thread.start();
```

如果测试方法结束时不清理这个线程，它可能继续在后台运行。

这会带来两个问题：

```text
线程后面失败了，当前测试可能已经被认为通过；
线程继续运行，可能影响后续测试。
```

所以并发测试里的 `tearDown` 往往会做这些事：

```text
等待测试创建的线程结束；
中断还没结束的线程；
关闭 ExecutorService；
检查工作线程中记录的异常；
如果发现子线程失败，就让当前测试失败。
```

## 和书中这段话的关系

书中提到 JSR 166 测试基类会在 `tearDown` 阶段传递和报告失败信息。

意思是：

```text
测试过程中，工作线程里的异常会先被记录下来；
测试结束后，tearDown 会统一检查这些异常；
如果有异常，就报告给测试框架。
```

这样可以避免并发测试“假通过”。

## 总结

一句话理解：

```text
setUp 是测试前准备；
tearDown 是测试后收尾。
```

在并发测试中，`tearDown` 的重点是：

```text
回收测试创建的线程；
清理测试资源；
把工作线程中的失败汇总到测试结果里。
```
