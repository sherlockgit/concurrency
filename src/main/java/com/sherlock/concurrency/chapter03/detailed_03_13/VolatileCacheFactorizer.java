package com.sherlock.concurrency.chapter03.detailed_03_13;


import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter03.detailed_03_12.OneValueCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 使用指向不可变容器对象的volatile类型引用以缓存最新的结果
 *
 * 对于在访问和更新多个相关变量时出现的竞争条件问题，可以通过将这些变量全部保存在一个不可变对象中来消除。
 * 如果是一个可变的对象，那么就必须使用锁来确保原子性。如果是一个不可变对象，那么当线程获得了对该对象的引用后，
 * 就不必担心另一个线程会修改对象的状态。如果要更新这些变量，那么可以创建一个新的容器对象，
 * 但其他使用原有对象的线程仍然会看到对象处于一致的状态。
 *
 * 程序中的VolatileCacheFactorizer使用了 OneValueCache来保存缓存的数值及其因数。
 * 当-个线程将volatile类型的cache设置为引用一个新的OneValueCache时，其他线程就会立即看到新缓存的数据。
 */
@RestController("/VolatileCacheFactorizer")
@ThreadSafe
public class VolatileCacheFactorizer {


    private volatile OneValueCache cache = new OneValueCache(null,null);

    @GetMapping("/service")
    public List<Long> service(@RequestParam(required = false,name = "value") Long value){
        List<Long> factors = cache.getFactors(value);
        if (factors == null) {
            factors = getAllFactors(value);
            cache = new OneValueCache(value,factors);;
        }
        return factors;
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
