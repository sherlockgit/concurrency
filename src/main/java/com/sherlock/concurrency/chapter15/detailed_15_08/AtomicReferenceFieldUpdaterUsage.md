# AtomicReferenceFieldUpdater 的用法

## 问题

`AtomicReferenceFieldUpdater` 是什么？应该怎么用？

## 核心理解

`AtomicReferenceFieldUpdater` 可以理解成：

```text
给某个对象里的 volatile 引用字段，创建一个可以执行 CAS 的操作器。
```

它自己不保存值，而是操作别的对象中的某个字段。

## 基本写法

假设有一个链表节点：

```java
class Node<E> {
    volatile Node<E> next;
}
```

可以为 `next` 字段创建一个 updater：

```java
private static final AtomicReferenceFieldUpdater<Node, Node> NEXT_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "next");
```

然后就可以对某个节点的 `next` 字段执行 CAS：

```java
NEXT_UPDATER.compareAndSet(node, null, newNode);
```

这句话的含义是：

```text
如果 node.next 当前仍然是 null，
就原子地把 node.next 改成 newNode；
否则更新失败。
```

## 字段要求

被 updater 操作的字段必须满足这些条件：

- 字段必须是 `volatile`；
- 字段不能是 `final`；
- 字段必须是引用类型，不能是 `int`、`long` 这种基本类型；
- 字段名通过字符串指定，例如 `"next"`；
- updater 所在的代码必须有权限访问这个字段。

例如：

```java
private volatile Node<E> head;
private volatile Node<E> tail;
```

然后可以分别创建 updater：

```java
private static final AtomicReferenceFieldUpdater<MyQueue, Node> HEAD_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(MyQueue.class, Node.class, "head");

private static final AtomicReferenceFieldUpdater<MyQueue, Node> TAIL_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(MyQueue.class, Node.class, "tail");
```

## 和 AtomicReference 的区别

如果使用 `AtomicReference`，通常会这样写：

```java
class Node<E> {
    final AtomicReference<Node<E>> next = new AtomicReference<Node<E>>();
}
```

如果使用 `AtomicReferenceFieldUpdater`，则可以这样写：

```java
class Node<E> {
    volatile Node<E> next;
}
```

两者的区别是：

- `AtomicReference`：每个字段都要额外创建一个包装对象；
- `AtomicReferenceFieldUpdater`：字段本身仍然是普通 `volatile` 引用，只是通过 updater 对它做 CAS；
- 在链表、队列这类节点很多的数据结构中，updater 可以减少对象数量和内存开销。

## 结合 15-8 的例子

项目中的 15-8 示例：

[ConcurrentLinkedQueueFieldUpdaterDemo.java](D:/project/concurrency/concurrency/src/main/java/com/sherlock/concurrency/chapter15/detailed_15_08/ConcurrentLinkedQueueFieldUpdaterDemo.java)

里面的节点是这样定义的：

```java
private static class Node<E> {
    final E item;
    volatile Node<E> next;

    private static final AtomicReferenceFieldUpdater<Node, Node> NEXT_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "next");
}
```

入队时，队列要把新节点挂到当前尾节点后面：

```java
currentTail.casNext(null, newNode)
```

本质就是：

```text
如果 currentTail.next 还是 null，
说明 currentTail 仍然是真正的尾节点，
就把 currentTail.next 改成 newNode。
```

如果 CAS 失败，说明别的线程已经先一步插入了节点，当前线程重新读取队尾并重试。

## 适用场景

`AtomicReferenceFieldUpdater` 常用于高性能并发数据结构，例如：

- 无阻塞链表；
- 无阻塞队列；
- 节点数量很多、希望减少额外包装对象的结构；
- 需要对对象内部某个引用字段做 CAS 的场景。

## 一句话总结

```text
AtomicReferenceFieldUpdater = 对已有 volatile 引用字段执行 CAS 的工具。
```

它适合在高性能并发数据结构中使用，用来减少 `AtomicReference` 包装对象带来的额外内存开销。

