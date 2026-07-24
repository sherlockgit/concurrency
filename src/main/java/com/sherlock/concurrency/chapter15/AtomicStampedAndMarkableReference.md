# AtomicStampedReference 和 AtomicMarkableReference

## 问题

`AtomicStampedReference` 和 `AtomicMarkableReference` 是什么？它们和普通的 `AtomicReference` 有什么区别？

## 先说 ABA 问题

普通 `AtomicReference` 只比较引用本身。

例如：

```text
线程 A 看到 ref = A
线程 A 准备 CAS(A, B)

线程 B 把 ref 从 A 改成 C
线程 B 又把 ref 从 C 改回 A

线程 A 执行 CAS(A, B)
CAS 成功
```

从线程 A 的角度看，`ref` 还是 `A`，所以 CAS 成功。

但实际上，`ref` 中间已经经历过：

```text
A -> C -> A
```

这就是 ABA 问题。

## AtomicStampedReference

`AtomicStampedReference` 保存的是：

```text
引用 + int 版本号
```

创建方式：

```java
AtomicStampedReference<Node> ref =
        new AtomicStampedReference<Node>(node, 0);
```

CAS 时不仅比较引用，还比较版本号：

```java
ref.compareAndSet(oldNode, newNode, oldStamp, oldStamp + 1);
```

含义是：

```text
只有当引用仍然是 oldNode，
并且版本号仍然是 oldStamp，
才把引用改成 newNode，
并把版本号改成 oldStamp + 1。
```

这样即使引用从 `A` 变成 `C`，又变回 `A`，版本号也已经变化：

```text
(A, 0) -> (C, 1) -> (A, 2)
```

线程 A 如果还拿着旧快照 `(A, 0)` 去 CAS，就会失败。

## AtomicStampedReference 适合什么场景

它适合这些场景：

- 需要知道引用中间是否发生过变化；
- 需要解决 ABA 问题；
- 版本号本身有意义；
- 希望通过版本号辅助调试并发状态变化。

可以简单理解为：

```text
AtomicStampedReference 关心“引用变过几次”。
```

## AtomicMarkableReference

`AtomicMarkableReference` 保存的是：

```text
引用 + boolean 标记
```

创建方式：

```java
AtomicMarkableReference<Node> ref =
        new AtomicMarkableReference<Node>(node, false);
```

CAS 时比较引用，也比较标记：

```java
ref.compareAndSet(oldNode, newNode, false, true);
```

含义是：

```text
只有当引用仍然是 oldNode，
并且标记仍然是 false，
才把引用改成 newNode，
并把标记改成 true。
```

## AtomicMarkableReference 适合什么场景

它适合只需要一个二值状态的场景，例如：

- 节点是否已经被逻辑删除；
- 链表中的边是否已经被标记；
- 某个引用是否处于特殊状态。

例如无锁链表中，删除节点通常分两步：

```text
第一步：把节点标记为已删除
第二步：其他线程看到标记后，帮忙把它从链表中物理移除
```

这里的“是否已经删除”只需要一个 `boolean`，不需要完整版本号。

可以简单理解为：

```text
AtomicMarkableReference 关心“引用有没有被标记”。
```

## 三者对比

| 类 | 保存内容 | 主要用途 |
| --- | --- | --- |
| `AtomicReference` | 引用 | 原子替换引用 |
| `AtomicStampedReference` | 引用 + `int stamp` | 解决 ABA，记录版本变化 |
| `AtomicMarkableReference` | 引用 + `boolean mark` | 标记状态，例如逻辑删除 |

## 代码对比

普通引用 CAS：

```java
AtomicReference<Node> ref = new AtomicReference<Node>(oldNode);
ref.compareAndSet(oldNode, newNode);
```

带版本号的 CAS：

```java
AtomicStampedReference<Node> ref =
        new AtomicStampedReference<Node>(oldNode, 0);

int[] stampHolder = new int[1];
Node current = ref.get(stampHolder);
int currentStamp = stampHolder[0];

ref.compareAndSet(current, newNode, currentStamp, currentStamp + 1);
```

带标记的 CAS：

```java
AtomicMarkableReference<Node> ref =
        new AtomicMarkableReference<Node>(oldNode, false);

boolean[] markHolder = new boolean[1];
Node current = ref.get(markHolder);
boolean currentMark = markHolder[0];

ref.compareAndSet(current, newNode, currentMark, true);
```

## 一句话总结

```text
AtomicStampedReference = 引用 + 版本号，重点解决 ABA。
AtomicMarkableReference = 引用 + 标记位，重点表示逻辑状态。
```

