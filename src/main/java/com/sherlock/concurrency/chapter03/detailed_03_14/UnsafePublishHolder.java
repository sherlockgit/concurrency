package com.sherlock.concurrency.chapter03.detailed_03_14;

import com.sherlock.concurrency.annoations.NotThreadSafe;

/**
 * 在没有足够同步的情况下发布对象
 *
 * 可见性问题：
 * 当一个线程在 initialize() 中创建 Holder 对象时，新创建的对象状态可能只存在于该线程的本地内存/缓存中
 * 其他线程可能看到 holder 引用已经被设置（非null），但对象内部状态（如构造函数的参数值42）可能还没有被其他线程看到
 */
@NotThreadSafe
public class UnsafePublishHolder {

    public Holder holder;

    public void initialize(){
        holder = new Holder(42);
    }
}
