# JVM 类初始化与对象初始化

## 问题一：类是在什么时候被 JVM 初始化的

类初始化发生在 JVM 第一次**主动使用**这个类之前。

需要区分类的生命周期阶段：

```text
加载（Loading）
    ->
链接（Linking：验证、准备、解析）
    ->
初始化（Initialization）
    ->
执行静态字段赋值和 static 代码块
```

类可能已经被加载和链接，但尚未初始化。初始化一般是按需触发的。

### 常见的初始化触发条件

以下操作会触发相关类的初始化：

```java
new User();                  // 创建对象
User.doWork();               // 调用该类声明的静态方法
int n = User.count;          // 读取非编译期常量的静态字段
User.count = 10;             // 写入静态字段
Class.forName("pkg.User");   // 默认会初始化类
```

初始化时，JVM 会执行编译器生成的类初始化方法 `<clinit>`，其中包含：

```text
静态字段的显式赋值；
static 代码块；
它们按照源码中的顺序执行。
```

例如：

```java
class User {
    static int count = loadCount();

    static {
        System.out.println("User initialized");
    }
}
```

### 不会触发初始化的典型情况

下面的 `MAX` 是编译期常量，编译器通常会把 `100` 直接内联到调用方，因此不一定初始化 `Constants`：

```java
class Constants {
    static final int MAX = 100;
}

System.out.println(Constants.MAX);
```

取得类字面量通常也不会初始化类：

```java
Class<?> type = User.class;
```

以下调用会加载类，但明确要求不初始化：

```java
Class.forName("pkg.User", false, loader);
```

### 多线程下的类初始化

假设两个线程同时首次使用 `User`：

```text
线程 A                         线程 B

首次主动使用 User
    |
开始初始化 User
    |
执行静态字段赋值、static 块
                                首次主动使用 User
                                    |
                                发现 User 正在初始化
                                    |
                                等待线程 A 完成
    |
初始化完成
    |
发布初始化结果
                                |
                                继续使用 User
```

JVM 保证：

```text
一个类只会成功初始化一次；
同一时刻只有一个线程执行初始化代码；
其他线程会等待初始化完成；
初始化期间的写入，对后续使用该类的线程可见。
```

对于普通类，初始化前还会先初始化它的父类。更完整的触发规则与初始化过程见 [JLS 12.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4)。

### 与 16.6 的关系

16.6 使用静态内部类实现延迟初始化：

```java
public class ResourceFactory {
    private static class ResourceHolder {
        static Resource resource = new Resource();
    }

    public static Resource getResource() {
        return ResourceHolder.resource;
    }
}
```

首次使用 `ResourceFactory` 时，不一定会初始化 `ResourceHolder`。

只有首次调用 `getResource()` 并访问 `ResourceHolder.resource` 时，才会触发内部类 `ResourceHolder` 的初始化，进而创建 `Resource`。JVM 保证它只创建一次并安全发布。

---

## 问题二：类初始化和对象初始化有什么区别

```text
类初始化：初始化“类本身”的静态状态。
对象初始化：初始化“某一个对象实例”的实例状态。
```

| 对比项 | 类初始化 | 对象初始化 |
| --- | --- | --- |
| 操作对象 | 类 | 某一个对象实例 |
| 执行次数 | 每个类加载器下通常一次 | 每创建一个对象执行一次 |
| 初始化内容 | `static` 字段、`static {}` 代码块 | 实例字段、实例初始化块、构造器 |
| 编译器生成的方法 | `<clinit>` | `<init>`，即构造器逻辑 |
| 并发保证 | JVM 保证只初始化一次并安全发布 | 构造完成本身不等于安全发布 |
| 是否需要创建对象 | 不需要 | 必须创建对象 |

### 代码示例

```java
class Example {
    static int staticValue = initStatic();

    int instanceValue = initInstance();

    static {
        System.out.println("static block");
    }

    {
        System.out.println("instance block");
    }

    Example() {
        System.out.println("constructor");
    }

    static int initStatic() {
        System.out.println("static field");
        return 1;
    }

    int initInstance() {
        System.out.println("instance field");
        return 2;
    }
}
```

第一次执行：

```java
new Example();
```

大致顺序如下：

```text
1. 如果 Example 尚未初始化，先进行类初始化

   static field
   static block

2. 为新对象分配内存，实例字段先获得默认值

   instanceValue == 0

3. 执行父类构造过程

4. 执行当前类的实例字段初始化和实例初始化块

   instance field
   instance block

5. 执行当前类构造器主体

   constructor
```

第二次执行：

```java
new Example();
```

只会再次执行对象初始化：

```text
instance field
instance block
constructor
```

不会再次执行静态字段初始化或 `static` 代码块，因为 `Example` 已经完成类初始化。

### 最重要的并发区别：构造完成不等于安全发布

下面的对象在 A 线程中虽然已经构造完成，但仍没有被安全发布：

```java
// 线程 A
Resource resource = new Resource();
shared = resource; // 无同步发布
```

线程 B 若没有通过锁、`volatile`、并发队列等方式取得 `shared`，则可能看不到最新引用，也可能看不到对象内部完整的构造状态。这正是 16.3 `UnsafeLazyInitialization` 的问题。

而 16.5 的写法不同：

```java
private static Resource resource = new Resource();
```

这里的过程是：

```text
类初始化
    ->
new Resource()，完成对象初始化
    ->
resource 指向该对象
    ->
JVM 完成类初始化并安全发布
```

对象初始化发生在类初始化内部，所以 JVM 的类初始化协议会把 `resource` 以及 `Resource` 的初始状态安全发布给后续使用该类的线程。

### 类初始化只保护初始状态

类初始化不会自动保护对象生命周期中之后的修改：

```java
class ResourceHolder {
    static final Resource RESOURCE = new Resource();
}

// 初始化完成后，某线程又修改对象
ResourceHolder.RESOURCE.setName("new value");
```

这次 `setName` 不属于类初始化。若其他线程要可靠看到这个修改，仍需要锁、`volatile`、原子变量或其他同步机制。

## 总结

```text
类初始化解决：静态状态何时创建、如何安全发布。
对象初始化解决：某个具体实例如何被构造出来。
对象构造完成后，仍必须通过正确同步发布，其他线程才能安全使用它。
```

相关代码：[ResourceFactory.java](D:/project/concurrency/concurrency/src/main/java/com/sherlock/concurrency/chapter16/detailed_16_06/ResourceFactory.java)
