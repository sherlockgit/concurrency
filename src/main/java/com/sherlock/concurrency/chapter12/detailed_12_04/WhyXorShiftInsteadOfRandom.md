# 为什么不用 Random，而要写 XorShift

## 问题

这个伪随机数生成器为什么要写成 XorShift，而不是直接使用：

```java
Random random = new Random();
```

## 核心原因

主要原因不是 `Random` 不能用，而是：

```text
并发测试里不希望随机数生成器本身变成性能瓶颈或干扰测试结果。
```

在第 12 章后续的并发压力测试中，多个生产者和消费者线程会频繁生成随机数。

如果所有线程共享同一个 `Random`：

```java
Random random = new Random();

int value = random.nextInt();
```

那么所有线程都会竞争同一个随机数生成器的内部状态。

这样测试结果可能被污染。

你本来想测试：

```text
BoundedBuffer 在多线程下的吞吐和正确性。
```

但实际测到的可能包含：

```text
多个线程竞争 Random 内部状态的开销。
```

## XorShift 的设计目的

`XorShift` 的使用方式通常是：

```java
XorShift random = new XorShift();
```

每个测试线程都创建自己的实例。

它内部只有一个状态字段：

```java
int x;
```

每个线程只修改自己的 `x`，不和其他线程共享。

所以它不需要：

```text
synchronized
CAS
共享锁
共享随机种子竞争
```

这使得随机数生成本身非常轻量。

## 为什么这对并发测试重要

并发测试应该尽量让瓶颈落在被测对象上。

如果测试代码自身有严重竞争，比如所有线程都争同一个 `Random`，就会让测试结果变得不清楚：

```text
到底是 BoundedBuffer 慢？
还是 Random 被多个线程竞争导致慢？
```

使用每线程独立的 `XorShift` 可以减少这种干扰。

## 能不能每个线程一个 Random

可以。

如果每个线程都自己创建一个 `Random`：

```java
Random random = new Random();
```

而不是所有线程共享一个 `Random`，竞争问题会小很多。

但是书中的 `XorShift` 有几个优点：

```text
实现非常轻量；
速度快；
不需要同步；
足够满足测试数据生成；
方便让每个线程有独立随机序列。
```

## 现代 Java 中的替代选择

在现代 Java 中，更常见的选择是：

```java
ThreadLocalRandom.current().nextInt()
```

或者：

```java
SplittableRandom
```

它们都比共享一个 `Random` 更适合并发场景。

不过这本书写作较早，当时这些工具还没有像现在这样常用。

## 总结

一句话理解：

```text
XorShift 不是为了生成更安全的随机数，
而是为了在并发测试中快速生成测试数据，并避免随机数生成器本身成为共享竞争点。
```
