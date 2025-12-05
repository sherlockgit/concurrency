package com.sherlock.concurrency.chapter02.detailed_02_01;

import com.sherlock.concurrency.annoations.ThreadSafe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一个无状态的servlet
 *
 * 与大多数Servlet相同，StatelessFactorizer是无状态的:它既不包含任何域，
 * 也不包含任何对其他类中域的引用。计算过程中的临时状态仅存在于线程栈上的局部变量中，
 * 并且只能由正在执行的线程访问。访问StatelessFactorizer的线程不会影响另一个访问
 * 同一个StatelessFactorizer的线程的计算结果，因为这两个线程并没有共享状态，
 * 就好像它们都在访问不同的实例。由于线程访问无状态对象的行为并不会影响其他线程中操作的正确性，
 * 因此无状态对象是线程安全的。
 */
@RestController("/StatelessFactorizer")
@ThreadSafe
public class StatelessFactorizer {



    @GetMapping("/service")
    public List<Long> service(@RequestParam(required = false,name = "value") Long value){
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
