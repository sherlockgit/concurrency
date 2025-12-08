package com.sherlock.concurrency.chapter02.detailed_02_05;

import com.sherlock.concurrency.annoations.NotThreadSafe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 该 Servlet 在没有足够原子性保证的情况下对其最近计算结果进行缓存(不要这么做)
 *
 * 在线程安全性的定义中要求，多个线程之间的操作无论采用何种执行时序或交替方式，
 * 都要保证不变性条件不被破坏。UnsafeCachingFactorizer的不变性条件之一是:
 * 在lastFactors中缓存的因数之积应该等于在 lastNumber 中缓存的数值。
 * 只有确保了这个不变性条件不被破坏上面的 Servlet才是正确的。
 * 当在不变性条件中涉及多个变量时，各个变量之间并不是彼此独立的，
 * 而是某个变量的值会对其他变量的值产生约束。因此，当更新某一个变量时，需要在同一个原子操作中对其他变量同时进行更新。
 *
 * 在某些执行时序中，UnsafeCachingFactorizer可能会破坏这个不变性条件。
 * 在使用原子引用的情况下，尽管对set方法的每次调用都是原子的，但仍然无法同时更新1astNumber 和lastFactors。
 * 如果只修改了其中一个变量，那么在这两次修改操作之间，其他线程将发现不变性条件被破坏了。
 * 同样，我们也不能保证会同时获取两个值:在线程A获取这两个值的过程中，线程 B可能修改了它们，这样线程A也会发现不变性条件被破坏了。
 */
@RestController("/UnsafeCachingFactorizer")
@NotThreadSafe
public class UnsafeCachingFactorizer {

    private final AtomicReference<Long> lastNumber = new AtomicReference<Long>();

    private final AtomicReference<List<Long>> lastFactors = new AtomicReference<List<Long>>();


    @GetMapping("/service")
    public List<Long> service(@RequestParam(required = false,name = "value") Long value){
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
