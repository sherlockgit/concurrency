package com.sherlock.concurrency.chapter03.detailed_03_15;

/**
 * 由于未被正确发布，因此这个类可能出现故障
 *
 * 你不能指望一个尚未被完全创建的对象拥有完整性。某个观察该对象的线程将看到对象处于不一致的状态，
 * 然后看到对象的状态突然发生变化，即使线程在对象发布后还没有修改过它。事实上，如果程序中的Holder
 * 使用程序3-14中的不安全发布方式，那么另一个线程在调用 assertSanity时将抛出AssertionError
 *
 * 由于没有使用同步来确保Holder对象对其他线程可见，因此将Holder称为"未被正确发布"。
 * 在未被正确发布的对象中存在两个问题。首先，除了发布对象的线程外，其他线程可以看到的Holder域是一个失效值，
 * 因此将看到一个空引用或者之前的旧值。然而，更糟糕的情况是，线程看到Holder引用的值是最新的，
 * 但Holder状态的值却是失效的。情况变得更加不可预测的是，某个线程在第一次读取域时得到失效值，
 * 而再次读取这个域时会得到一个更新值，这也是assertSanity抛出 AssertionError的原因。
 */
public class Holder {

    private int num;

    public Holder(int num) {
        this.num = num;
    }

    public void assertSanity(){
        if(num != num)
            throw new AssertionError("this statement is false.");
    }
}
