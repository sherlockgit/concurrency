package com.sherlock.concurrency.chapter02.detailed_02_04;

import com.sherlock.concurrency.annoations.NotThreadSafe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 使用 AtomicLong 类型的变量来统计已处理请求的数量
 *
 * 在 java.util.concurrent.atomic包中包含了一些原子变量类，用于实现在数值和对象引用上的原子状态转换。
 * 通过用AtomicLong来代替long类型的计数器，能够确保所有对计数器状态的访问操作都是原子的。
 * 由于Servlet的状态就是计数器的状态，并且计数器是线程安全的，因此这里的 Servlet 也是线程安全的。
 */
@RestController("/StatelessFactorizer")
@NotThreadSafe
public class CountingFactorizer {

    private final AtomicLong count = new AtomicLong(0);

    @GetMapping("/service")
    public List<Long> service(@RequestParam(required = false,name = "value") Long value){
        count.incrementAndGet();
        return getAllFactors(value);
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
