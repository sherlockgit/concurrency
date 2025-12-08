package com.sherlock.concurrency.chapter02.detailed_02_06;

import com.sherlock.concurrency.annoations.NotRecommend;
import com.sherlock.concurrency.annoations.NotThreadSafe;
import com.sherlock.concurrency.annoations.ThreadSafe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 这个 Serviet能正确地缓存最新的计算结果，但并发性却非常糟糕(不要这么做)
 *
 * Java提供了一种内置的锁机制来支持原子性:同步代码块(SynchronizedBlock)。
 * 同步代码块包括两部分:一个作为锁的对象引用，一个作为由这个锁保护的代码块。
 * 以关键字synchronized 来修饰的方法就是一种横跨整个方法体的同步代码块，
 * 其中该同步代码块的锁就是方法调用所在的对象。静态的synchronized 方法以Class 对象作为锁。
 *
 */
@RestController("/SynchronizedFactorizer")
@ThreadSafe
@NotRecommend
public class SynchronizedFactorizer {

    private final AtomicReference<Long> lastNumber = new AtomicReference<Long>();

    private final AtomicReference<List<Long>> lastFactors = new AtomicReference<List<Long>>();


    @GetMapping("/service")
    public synchronized List<Long> service(@RequestParam(required = false,name = "value") Long value){
        if (value.equals(lastNumber.get())) {
            return lastFactors.get();
        }else {
            List<Long> longs = getAllFactors(value);
            lastNumber.set(value);
            lastFactors.set(longs);
            return longs;
        }
    }


    public List<Long> getAllFactors(long n) {
        List<Long> factors = new ArrayList<>();

        if (n <= 0) {
            return factors;
        }

        // 添加1和n本身
        factors.add(1L);
        if (n > 1) {
            factors.add(n);
        }

        // 寻找其他因数
        for (long i = 2; i * i <= n; i++) {
            if (n % i == 0) {
                factors.add(i);
                // 避免重复添加平方根
                if (i != n / i) {
                    factors.add(n / i);
                }
            }
        }

        Collections.sort(factors);
        return factors;
    }

}
