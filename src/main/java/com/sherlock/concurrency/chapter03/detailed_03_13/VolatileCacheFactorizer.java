package com.sherlock.concurrency.chapter03.detailed_03_13;


import com.sherlock.concurrency.annoations.NotThreadSafe;
import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter03.detailed_03_12.OneValueCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
