package com.sherlock.concurrency.chapter03.detailed_03_12;


import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.Arrays;

/**
 *  对数值及其因数分解结果进行缓存的不可变容器类
 *
 *  在前面的 UnsafeCachingFactorizer类中，我们尝试用两个AtomicReferences 变量来保存最新的数值及其因数分解结果，
 *  但这种方式并非是线程安全的，因为我们无法以原子方式来同时读取或更新这两个相关的值。
 *  同样，用volatile 类型的变量来保存这些值也不是线程安全的。然而，在某些情况下，不可变对象能提供一种弱形式的原子性。
 *
 *  因式分解 Servlet 将执行两个原子操作:更新缓存的结果，以及通过判断缓存中的数值是否等于请求的数值来决定是否直接读取缓存中的因数分解结果。
 *  每当需要对一组相关数据以原子方式执行某个操作时，就可以考虑创建一个不可变的类来包含这些数据，例如程序清单中的OneValueCache
 */
@ThreadSafe
public class OneValueCache {

    private final Long lastNumber;
    private final Long[] lastFactors;

    public OneValueCache(Long i, Long[] factors) {
        this.lastNumber = i;
        this.lastFactors = Arrays.copyOf(factors,factors.length);
    }

    public Long[] getFactors(Long i){
        if (lastNumber == null || !lastNumber.equals(i)) {
            return null;
        }else {
            return Arrays.copyOf(lastFactors,lastFactors.length);
        }
    }
}
