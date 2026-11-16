# JVM 类初始化为何能保证可见性

## 问题

为什么说 JVM 在类初始化期间获得初始化锁，因此静态初始化中的内存写入会自动对所有线程可见？

## 一句话理解

JVM 会在类首次被主动使用前，确保：

```text
只有一个线程执行静态初始化；
其他线程必须等初始化完成；
初始化完成时写入的状态，被安全发布给后续使用该类的线程。
```

例如：

```java
class Config {
    static int version;
    static Resource resource;

    static {
        version = 1;
        resource = new Resource("prod");
    }
}
```

线程 A 第一次主动使用 `Config` 时，会触发它的类初始化。初始化完成后，线程 B 再使用 `Config`，必须能看到：

```text
Config.version == 1
Config.resource != null
Config.resource 中构造期间设置好的初始字段值
```

## 类初始化状态

可以把每个类的状态抽象为：

```text
未初始化
    ->
正在由某个线程初始化
    ->
初始化完成
```

逻辑流程如下：

```text
线程 A                                 线程 B

首次主动使用 Config
    |
取得 Config 的初始化控制权
    |
执行静态字段初始化与 static 代码块
    |
version = 1
resource = new Resource("prod")
    |
标记 Config 已初始化
发布初始化结果
                                      首次主动使用 Config
                                          |
                                      确认 Config 初始化已完成
                                          |
                                      读取 version、resource 及其初始状态
```

若线程 B 在 A 初始化期间尝试使用 `Config`，B 会等待；A 初始化完成后，B 才能继续。

## Happens-Before 如何建立

可以按锁的语义理解这条链路：

```text
线程 A：
Resource 构造函数内的写入
    -> 程序顺序
resource = new Resource(...)
    -> 程序顺序
类初始化完成，发布并释放初始化控制
    -> 类初始化同步语义
线程 B 确认该类已经初始化
    -> 程序顺序
读取 resource 及其字段
```

由 Happens-Before 的传递性可得：

```text
线程 A 在静态初始化期间的写入
    happens-before
线程 B 在确认类初始化完成之后的读取。
```

因此，静态初始化可以安全发布对象引用及该对象构造时建立的状态。

## 不要把它理解成普通 synchronized 块

书中说 JVM 在初始化期间获得一个锁，重点是在说明它提供的线程安全和可见性效果。

严格来说，JLS 为每个类定义了一个初始化锁和一套初始化状态转换过程。JVM 不一定将整个 `static` 代码块都执行在一个普通 Java `synchronized` 块中；它可以使用内部状态和等待机制实现。但无论如何，实现都必须保证：

```text
只初始化一次；
其他线程不会在初始化完成前使用该类；
初始化写入对后续使用者可见。
```

JVM 也可以在确认不破坏这些内存语义的前提下，优化后续对初始化锁的实际获取。

## 与 16.4 的 synchronized 懒加载区别

16.4 的核心代码是：

```java
public static synchronized Resource getInstance() {
    if (resource == null) {
        resource = new Resource();
    }
    return resource;
}
```

它的初始化发生在第一次调用 `getInstance()` 时：

```text
检查、构造、赋值
都由 SafeLazyInitialization.class 的监视器锁保护。
```

静态初始化则是：

```java
private static final Resource RESOURCE = new Resource();
```

它在类初始化阶段创建对象，不是普通意义上的懒加载，但 JVM 自动保证其安全发布。

| 方式 | 对象创建时机 | 可见性来源 | 后续调用开销 |
| --- | --- | --- | --- |
| 16.4 `synchronized` 懒加载 | 首次调用 `getInstance()` | 同一把类锁的解锁和加锁 | 每次调用都要同步 |
| 静态初始化 | 类初始化阶段 | JVM 类初始化协议 | 后续直接读取静态字段 |

## 为什么静态初始化对象不需要 volatile 或 synchronized

下面的写法本身就是线程安全的：

```java
class ResourceHolder {
    private static final Resource RESOURCE = new Resource();

    public static Resource getInstance() {
        return RESOURCE;
    }
}
```

`RESOURCE` 的构造和赋值发生在类初始化阶段。JVM 已经保证初始化结果会在其他线程开始使用这个类之前发布，因此不必再为“首次构造和发布”额外添加 `volatile` 或 `synchronized`。

## 这个保证的边界

类初始化只保证对象的初始状态安全可见，不会自动保护初始化之后的并发修改：

```java
class ResourceHolder {
    static final Resource RESOURCE = new Resource();
}

// 某个线程后续修改
ResourceHolder.RESOURCE.setName("new value");
```

这次 `setName` 不在类初始化阶段，其他线程读取 `name` 时仍需要正确同步。否则会产生新的数据竞争，可能读到旧值，也可能观察到不一致状态。

因此：

```text
类初始化保证“构造完成时”的安全发布；
不能替代对象整个生命周期中的并发控制。
```

## 补充：类加载和类初始化不是一回事

“类加载后立刻初始化”是便于理解的简化说法。更精确地说，类可能已经被加载和链接，但初始化会延后到首次主动使用之前。

例如，普通静态方法调用、读取或写入非编译期常量的静态字段、创建类实例等，都会触发类初始化。

但下面这种编译期常量可能被直接内联，不一定触发类初始化：

```java
static final int VALUE = 1;
```

## 参考资料

- [JLS 12.4.2：类和接口的详细初始化过程](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.2)
- [SafeLazyInitialization.java](D:/project/concurrency/concurrency/src/main/java/com/sherlock/concurrency/chapter16/detailed_16_04/SafeLazyInitialization.java)
