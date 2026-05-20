package com.sherlock.concurrency.chapter05.detailed_05_14;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;

/**
 * 使用Semaphore为容器设置边界
 * * *
 * 计数信号量(Counting Semaphore)用来控制同时访问某个特定资源的操作数量，
 * * 或者同时执行某个指定操作的数量。计数信号量还可以用来实现某种资源池，或者对容器施加边界。
 *
 * Semaphore中管理着一组虚拟的许可(permit)，许可的初始数量可通过构造函数来指定。
 * * 在执行操作时可以首先获得许可(只要还有剩余的许可)，并在使用以后释放许可。
 * * 如果没有许可，那么acquire将阻塞直到有许可(或者直到被中断或者操作超时)。
 * * release方法将返回一个许可给信号量。计算信号量的一种简化形式是二值信号量，
 * * 即初始值为1的Semaphore。二值信号量可以用做互斥体(mutex)，并具备不可重人的加锁语义:谁拥有这个唯一的许可，谁就拥有了互斥锁。
 *
 * Semaphore可以用于实现资源池，例如数据库连接池。我们可以构造一个固定长度的资源池，
 * * 当池为空时，请求资源将会失败，但你真正希望看到的行为是阻塞而不是失败，并且当池非空时解除阻塞。
 * * 如果将Semaphore的计数值初始化为池的大小，并在从池中获取一个资源之前首先调用acquire方法获取一个许可，
 * * 在将资源返回给池之后调用release释放许可，那么acquire将一直阻塞直到资源池不为空。
 * * (在构造阻塞对象池时，一种更简单的方法是使用BlockingQueue来保存池的资源。)
 *
 * 同样，你也可以使用Semaphore将任何一种容器变成有界阻塞容器，如程序BoundedHashSet 所示。
 * * 信号量的计数值会初始化为容器容量的最大值。add操作在向底层容器中添加一个元素之前，
 * * 首先要获取一个许可。如果add操作没有添加任何元素，那么会立刻释放许可。同样，
 * * remove操作释放一个许可，使更多的元素能够添加到容器中。底层的Set实现并不知道关于边界的任何信息，这是由BoundedHashSet来处理的。
 */
public class BoundedHashSet<T> {
    // 底层使用的线程安全 Set（由 Collections.synchronizedSet 包装）
    private final Set<T> set;
    // 信号量，用于限制集合的最大容量（许可数 = 边界大小）
    private final Semaphore sem;

    // 构造函数，指定边界大小
    public BoundedHashSet(int bound) {
        this.set = Collections.synchronizedSet(new HashSet<>());
        // 初始化信号量，许可数为 bound，表示最多允许 bound 个元素同时被添加
        sem = new Semaphore(bound);
    }

    // 添加元素，若集合已满则阻塞等待
    public boolean add(T o) throws InterruptedException {
        // 获取一个许可，如果当前没有可用许可则阻塞，直到有许可释放
        sem.acquire();
        boolean wasAdded = false;
        try {
            // 尝试将元素添加到集合中
            wasAdded = set.add(o);
            return wasAdded;
        } finally {
            // 如果添加失败（例如元素已存在），则释放之前获取的许可，避免浪费
            if (!wasAdded)
                sem.release();
        }
    }

    // 移除元素
    public boolean remove(Object o) {
        // 尝试从集合中移除元素
        boolean wasRemoved = set.remove(o);
        // 如果移除成功，释放一个信号量许可，表示集合空出了一个位置
        if (wasRemoved)
            sem.release();
        return wasRemoved;
    }
}
