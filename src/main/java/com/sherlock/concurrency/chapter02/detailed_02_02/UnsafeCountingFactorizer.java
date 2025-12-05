package com.sherlock.concurrency.chapter02.detailed_02_02;

import com.sherlock.concurrency.annoations.NotThreadSafe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 在没有同步的情况下统计已处理请求数量的Servlet(不要这么做)
 *
 * 不幸的是，UnsafeCountingFactorizer 并非线程安全的，
 * 尽管它在单线程环境中能正确运行。与前面的UnsafeSequence 一样，这个类很可能会丢失一些更新操作。
 * 虽然递增操作 ++count 是一种紧凑的语法，使其看上去只是一个操作，但这个操作并非原子的，
 * 因而它并不会作为一个不可分割的操作来执行。实际上，它包含了三个独立的操作:
 * 读取count的值，将值加1，然后将计算结果写入count。这是一个“读取-修改-写入”的操作序列，并且其结果状态依赖于之前的状态。
 *
 * 在并发编程中，这种由于不恰当的执行时序而出现不正确的结果是一种非常重要的情况，它有一个正式的名字:竞态条件(RaceCondition)
 */
@RestController("/StatelessFactorizer")
@NotThreadSafe
public class UnsafeCountingFactorizer {

    private long count = 0;

    @GetMapping("/service")
    public List<Long> service(@RequestParam(required = false,name = "value") Long value){
        count ++;
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
