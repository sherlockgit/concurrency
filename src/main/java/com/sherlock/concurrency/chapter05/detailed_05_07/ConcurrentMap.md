###  **额外的原子Map操作**

由于ConcurrentHashMap不能被加锁来执行独占访问，因此我们无法使用客户端加锁来创建新的原子操作，例如4.4.1节中对Vector增加原子操作“若没有则添加”。但是，一些常见的复合操作，例如“若没有则添加”、“若相等则移除(Remove-If-Equal)”和“若相等则替换(Replace-If-Equal)”等，都已经实现为原子操作并且在ConcurrentMap的接口中声明，如程序清单5-7所示。如果你需要在现有的同步Map中添加这样的功能，那么很可能就意味着应该考虑使用 ConcurrentMap 了。

####  **ConcurrentMap接口**

```java
public interface ConcurrentMap<K, V> extends Map<K, V> {
// 仅当 K 没有相应的映射值时才插入
    V putIfAbsent(K key, V value);

// 仅当 K 被映射到 V 时才移除
    boolean remove(K key, V value);

// 仅当 K 被映射到 oldValue 时才替换为 newValue
    boolean replace(K key, V oldValue, V newValue);

// 仅当 K 被映射到某个值时才替换为 newValue
    V replace(K key, V newValue);
}
```
