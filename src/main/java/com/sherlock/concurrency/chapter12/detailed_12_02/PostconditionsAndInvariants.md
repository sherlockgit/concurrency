# 后验条件和不变性条件

在并发测试中，书里经常提到两个概念：

```text
后验条件
不变性条件
```

这两个概念都是用来判断一个类的行为是否正确。

## 不变性条件

不变性条件是对象在任何稳定状态下都必须一直成立的规则。

例如一个有界缓冲区：

```java
class Buffer {
    int count;
    int capacity;
}
```

它的不变性条件可以是：

```text
0 <= count <= capacity
```

也就是说，缓冲区中的元素数量不能小于 0，也不能大于容量。

再比如一个区间对象：

```java
class Range {
    int lower;
    int upper;
}
```

它的不变性条件是：

```text
lower <= upper
```

无论调用了什么方法，只要对象回到稳定状态，这个条件都必须成立。

## 后验条件

后验条件是某个方法执行完成后必须成立的结果。

例如：

```java
buffer.put(x);
```

如果调用前缓冲区有 `n` 个元素，并且 `put` 成功，那么调用后应该有：

```text
count == n + 1
```

这就是 `put` 方法的后验条件。

再比如：

```java
buffer.take();
```

如果调用前缓冲区有 `n` 个元素，并且 `take` 成功，那么调用后应该有：

```text
count == n - 1
返回值是之前缓冲区里的某个元素
```

这也是后验条件。

## 二者区别

简单区分：

```text
不变性条件：对象一直必须满足的规则。
后验条件：某个方法执行完以后必须满足的规则。
```

不变性条件关注对象整体是否一直有效。

后验条件关注某个操作完成后是否达到预期效果。

## 放到 SemaphoreBoundedBuffer 中理解

在 12.1 的 `SemaphoreBoundedBuffer` 中，不变性条件包括：

```text
0 <= 已存元素数量 <= capacity
availableItems + availableSpaces == capacity
putPosition 和 takePosition 都在数组范围内
```

它的后验条件包括：

```text
构造完成后：buffer.isEmpty() == true
put 成功后：可取元素数量增加 1
take 成功后：可用空位数量增加 1
put 满 capacity 个元素后：buffer.isFull() == true
```

12.2 中的基础测试验证的就是后验条件。

例如：

```java
SemaphoreBoundedBuffer<Integer> buffer =
        new SemaphoreBoundedBuffer<Integer>(10);

assertTrue(buffer.isEmpty());
assertFalse(buffer.isFull());
```

这是在验证构造方法的后验条件：

```text
构造完成后，缓冲区应该为空，并且不应该为满。
```

再看：

```java
for (int i = 0; i < 10; i++) {
    buffer.put(i);
}

assertTrue(buffer.isFull());
assertFalse(buffer.isEmpty());
```

这是在验证多次 `put` 后的后验条件：

```text
放入 capacity 个元素后，缓冲区应该为满，并且不应该为空。
```

## 为什么并发程序特别关注它们

线程安全的核心之一就是：

```text
并发执行时，对象的不变性条件不能被破坏；
方法执行完成后，后验条件仍然要成立。
```

如果多个线程并发执行 `put` 和 `take`，导致：

```text
count < 0
count > capacity
availableItems + availableSpaces != capacity
```

那就是破坏了不变性条件。

如果 `put` 返回了，但元素实际上没有放进去；
或者 `take` 返回了重复元素；
或者构造完成后 `isEmpty()` 返回 false；
那就是破坏了后验条件。

所以，编写并发测试时，常见思路是：

```text
先测试简单后验条件；
再测试阻塞、中断、资源泄漏；
最后在高并发压力下验证不变性条件没有被破坏。
```
