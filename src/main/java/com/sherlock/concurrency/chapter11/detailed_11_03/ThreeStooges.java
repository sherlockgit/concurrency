package com.sherlock.concurrency.chapter11.detailed_11_03;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

/**
 * 锁消除的候选代码。
 *
 * <p>这是《Java 并发编程实战》中的 11.3。</p>
 *
 * <p>这个类在第 3 章也出现过，当时它用于说明：
 * 一个不可变对象内部可以使用可变对象保存状态，只要这个可变对象没有逸出，
 * 并且构造完成后不再被修改。</p>
 *
 * <p>放在第 11 章时，它强调的是另一个性能点：锁消除。</p>
 *
 * <p>{@link Vector} 是一个老的同步容器，它的许多方法内部都带 synchronized。
 * 按源码语义看，调用 {@code add} 时需要加锁。
 * 但在 {@link #getStoogeNames()} 中，Vector 是方法内部创建的局部对象，
 * 没有保存到字段里，也没有返回给调用方，因此其他线程不可能访问到它。</p>
 *
 * <p>如果 JVM 通过逃逸分析判断这个 Vector 没有逃逸出当前方法，
 * 那么它就可以证明这些 synchronized 没有并发保护价值，
 * 从而把相关加锁/解锁操作消除掉。这就是锁消除。</p>
 *
 * <p>需要注意：锁消除是 JVM 优化，不是程序员可以依赖的同步策略。
 * 写代码时仍然要保证语义正确；JVM 只是有机会把确定无用的锁优化掉。</p>
 */
@ThreadSafe
public final class ThreeStooges {

    /**
     * 保存三个人名的内部集合。
     *
     * <p>HashSet 本身是可变的，也不是线程安全容器。
     * 但它被 private final 字段封装在对象内部，
     * 构造完成后不再修改，也不会被返回给外部代码，
     * 因此这个类整体可以是线程安全的。</p>
     */
    private final Set<String> stooges = new HashSet<String>();

    public ThreeStooges() {
        stooges.add("Moe");
        stooges.add("Larry");
        stooges.add("Curly");
    }

    /**
     * 判断某个名字是否是三人组成员。
     *
     * <p>这里没有加锁，是因为 stooges 在构造后不会再被修改。
     * final 字段的正确发布语义保证构造完成后，其他线程可以看到构造期间写入的集合内容。</p>
     */
    public boolean isStooge(String name) {
        return stooges.contains(name);
    }

    /**
     * 返回三人组名字。
     *
     * <p>这个方法特意使用 {@link Vector}，因为 Vector 是同步容器。
     * 从普通源码角度看，下面三次 {@code add} 都会进入 Vector 的同步方法。
     * 但这个 Vector 是局部变量，只在当前方法中使用，没有被共享给其他线程，
     * 因此这些同步没有实际保护作用。</p>
     *
     * <p>现代 JVM 可能通过逃逸分析发现 names 不会逃逸，
     * 然后执行锁消除，去掉 Vector 内部无意义的加锁成本。</p>
     */
    public String getStoogeNames() {
        List<String> names = new Vector<String>();
        names.add("Moe");
        names.add("Larry");
        names.add("Curly");
        return names.toString();
    }

    /**
     * 简单演示。
     */
    public static void main(String[] args) {
        ThreeStooges threeStooges = new ThreeStooges();

        System.out.println(threeStooges.isStooge("Moe"));
        System.out.println(threeStooges.isStooge("Shemp"));
        System.out.println(threeStooges.getStoogeNames());
    }
}
