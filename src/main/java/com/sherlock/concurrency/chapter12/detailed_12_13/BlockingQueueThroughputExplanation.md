# LinkedBlockingQueue 和 ArrayBlockingQueue 的吞吐量差异

## 问题

如何理解书中这段话，"图12-2给出了一个在双核超线程机器上对这三个类的吞吐量测试结果，在测试中使用了一个包含256个元素的缓存，以及相应版本的TimedPutTakeTest。测试结果表明，LinkedBlockingQueue 的可伸缩性要高于 ArrayBlockingQueue。初看起来，这个结果有些奇怪:链表队列在每次插人元素时，都必须分配一个链表节点对象，这似乎比基于数组的队列执行了更多的工作。然而，虽然它拥有更好的内存分配与GC等开销，但与基于数组的队列相比，链表队列的put和take等方法支持并发性更高的访问，因为一些优化后的链接队列算法能将队列头节点的更新操作与尾节点的更新操作分离开来。由于内存分配操作通常是线程本地的，因此如果算法能通过多执行一些内存分配操作来降低竞争程度，那么这种算法通常具有更高的可伸缩性。(这种情况再次证明了，基于传统性能调优的直觉与提升可伸缩性的实际需求是背道而驰的。),"特别是里面 `LinkedBlockingQueue` 和 `ArrayBlockingQueue` 的区别？

书中大意是：

在双核超线程机器上，对几种阻塞队列做吞吐量测试。测试结果表明，`LinkedBlockingQueue` 的可伸缩性高于 `ArrayBlockingQueue`。这看起来有点反直觉，因为链表队列每次插入都要分配一个链表节点，理论上比数组队列做了更多工作，并且会增加内存分配和 GC 开销。但链表队列的 `put` 和 `take` 支持更高并发度，所以整体吞吐量更好。

## 核心结论

这段话的重点是：

**并发程序的吞吐量，不只取决于单次操作本身快不快，还取决于多个线程同时操作时会不会互相阻塞。**

单线程直觉通常会认为：

```text
数组访问直接
内存连续
对象分配少
GC 压力小
所以数组队列应该更快
```

但在多线程生产者-消费者场景中，还要考虑：

```text
put 和 take 是否会抢同一把锁
生产者之间是否会互相阻塞
消费者之间是否会互相阻塞
生产者和消费者能不能并行
```

如果锁竞争很严重，那么“数组访问更快”带来的优势可能被抢锁、等待、上下文切换抵消掉。

## ArrayBlockingQueue

`ArrayBlockingQueue` 是基于数组的有界阻塞队列。

可以简单理解为：

```text
固定大小数组
putIndex  指向下一个写入位置
takeIndex 指向下一个取出位置
count     表示当前元素数量
```

大致结构类似：

```text
items: [ A ][ B ][ C ][   ][   ]
          ↑              ↑
       takeIndex      putIndex
```

它的优点是：

```text
数组内存连续
不需要每次入队都创建节点
内存分配少
GC 压力小
缓存局部性通常更好
```

但它的并发限制也很明显。

典型实现中，`ArrayBlockingQueue` 使用一把锁保护队列状态。

也就是说：

```text
生产者 put
        ↓
获取同一把锁

消费者 take
        ↓
也获取同一把锁
```

可以画成：

```text
ArrayBlockingQueue

Producer put  ─┐
                ├── 同一把锁
Consumer take ──┘
```

即使一个线程只是往队尾放元素，另一个线程只是从队头取元素，它们仍然可能因为竞争同一把锁而不能并行。

所以在并发压力较大时，瓶颈可能变成：

```text
线程越多
  ↓
竞争同一把锁越激烈
  ↓
等待和上下文切换增加
  ↓
吞吐量提升受限
```

## LinkedBlockingQueue

`LinkedBlockingQueue` 是基于链表的阻塞队列。

可以简单理解为：

```text
head -> node1 -> node2 -> node3 -> tail
```

每次入队时，通常要创建一个新的节点：

```java
new Node(item)
```

所以它的缺点是：

```text
每次 put 可能分配新节点
内存分配次数更多
GC 压力更大
链表节点不一定连续
缓存局部性通常不如数组
```

从单线程角度看，这些都是劣势。

但它的优势在于并发结构。

典型实现中，`LinkedBlockingQueue` 将入队和出队拆成两把锁：

```text
put 使用 putLock
take 使用 takeLock
```

可以画成：

```text
LinkedBlockingQueue

Producer put  ── putLock

Consumer take ── takeLock
```

这意味着在很多情况下：

```text
生产者正在队尾 put
消费者可以同时在队头 take
```

因为它们操作的是链表的不同端，使用的也是不同锁。

这就是书里说的：

**链表队列的 put 和 take 支持并发性更高的访问。**

## 为什么 LinkedBlockingQueue 可伸缩性更好

可伸缩性关注的是：

```text
当线程数量增加时，吞吐量能不能继续提高
```

在生产者-消费者测试中，线程数量增加后，`ArrayBlockingQueue` 的单锁结构容易成为瓶颈：

```text
多个生产者抢锁
多个消费者抢锁
生产者和消费者也抢同一把锁
```

而 `LinkedBlockingQueue` 至少可以把一部分竞争拆开：

```text
生产者主要竞争 putLock
消费者主要竞争 takeLock
生产者和消费者之间的直接竞争减少
```

所以它虽然做了更多内存分配，但在多线程下可能反而吞吐量更好。

## 为什么这看起来反直觉

传统性能调优常常关注单线程成本：

```text
少分配对象
少访问指针
提高缓存命中率
减少方法调用
减少临时对象
```

按照这种直觉，数组队列应该优于链表队列。

但并发可伸缩性更关注：

```text
减少共享锁竞争
减少热点变量竞争
减少线程等待
减少上下文切换
提高不同线程可并行执行的比例
```

所以会出现这种结果：

```text
ArrayBlockingQueue 单次操作成本低，但并发竞争更集中。
LinkedBlockingQueue 单次操作成本高，但 put/take 并行度更高。
```

在多线程压力下，后者可能整体更快。

## 这是否说明 LinkedBlockingQueue 永远更好

不是。

这段话不能理解成：

```text
LinkedBlockingQueue 一定比 ArrayBlockingQueue 快。
```

更准确的理解是：

```text
在某些生产者-消费者并发场景下，
LinkedBlockingQueue 因为拆分了 put 和 take 的锁竞争，
可能比 ArrayBlockingQueue 有更好的吞吐量和可伸缩性。
```

具体选择还要看场景：

```text
如果更关注固定内存、较少 GC、容量严格受控，可以考虑 ArrayBlockingQueue。
如果生产者和消费者并发度较高，更关注吞吐量，可以考虑 LinkedBlockingQueue。
如果是线程池任务队列，还要额外考虑队列容量、拒绝策略、任务积压风险。
```

## 一句话总结

`ArrayBlockingQueue` 的优势是数组结构带来的低分配和较好局部性。

`LinkedBlockingQueue` 的优势是 `put` 和 `take` 可以通过不同锁获得更高并发度。

所以这段话真正想说明的是：

**在并发程序中，减少锁竞争有时比减少单次操作成本更重要。**
