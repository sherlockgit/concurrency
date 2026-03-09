package com.sherlock.concurrency.chapter04.detailed_04_10;

import com.sherlock.concurrency.annoations.NotThreadSafe;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 当委托失效时
 * * *
 * NumberRange不是线程安全的，没有维持对下界和上界进行约束的不变性条件。setLower和 setUpper 等方法都尝试维持不变性条件，
 * * 但却无法做到。setLower 和setUpper 都是“先检查后执行”的操作，但它们没有使用足够的加锁机制来保证这些操作的原子性。
 * * 假设取值范围为(0，10)，如果一个线程调用setLower(5)，而另一个线程调用setUpper(4)，那么在一些错误的执行时序中，
 * * 这两个调用都将通过检查，并且都能设置成功。结果得到的取值范围就是(5，4),，那么这是一个无效的状态。因此，
 * * 虽然AtomicInteger是线程安全的，但经过组合得到的类却不是。由于状态变量lower 和upper 不是彼此独立的，
 * * 因此NumberRange不能将线程安全性委托给它的线程安全状态变量。
 *
 * NumberRange可以通过加锁机制来维护不变性条件以确保其线程安全性，例如使用一个锁来保护lower 和upper。
 * * 此外，它还必须避免发布lower和upper，从而防止客户代码破坏其不变性条件。
 *
 * 如果某个类含有复合操作，例如NumberRange，那么仅靠委托并不足以实现线程安全性。在这种情况下，
 * * 这个类必须提供自己的加锁机制以保证这些复合操作都是原子操作，除非整个复合操作都可以委托给状态变量。
 */
@NotThreadSafe
public class NumberRange {

    /*如果一个类是由多个独立且线程安全的状态变量组成，并且在所有的操作中都不包含无效状态转换，那么可以将线程安全性委托给底层的状态变量。*/

    // 不变性条件：lower <= upper
    private final AtomicInteger lower = new AtomicInteger(0);
    private final AtomicInteger upper = new AtomicInteger(0);

    public void setLower(int i) {
        // 注意——不安全的“先检查后执行”
        if (i > upper.get()) {
            throw new IllegalArgumentException(
                    "can't set lower to " + i + " > upper");
        }
        upper.set(i);
    }

    public void setUpper(int i) {
        // 注意——不安全的“先检查后执行”
        if (i < lower.get()) {
            throw new IllegalArgumentException(
                    "can't set upper to " + i + " < lower");
        }
        lower.set(i);
    }

    public boolean isInRange(int i) {
        return (i >= lower.get() && i <= upper.get());
    }
}
