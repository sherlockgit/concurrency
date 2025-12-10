package com.sherlock.concurrency.chapter02.detailed_02_08;

import com.sherlock.concurrency.annoations.Recommend;
import com.sherlock.concurrency.annoations.ThreadSafe;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 缓存最近执行因数分解的数值及其计算结果的 Servlet
 *
 * 在 CachedFactorizer中不再使用 AtomicLong类型的命中计数器，而是使用了一个long 类型的变量。
 * 当然也可以使用AtomicLong类型，但使用CountingFactorizer带来的好处更多。对在单个变量上实现原子操作来说，原子变量是很有用的，
 * 但由于我们已经使用了同步代码块来构造原子操作，而使用两种不同的同步机制不仅会带来混乱，也不会在性能或安全性上带来任何好处，因此在这里不使用原子变量。
 *
 * 重新构造后的CachedFactorizer实现了在简单性(对整个方法进行同步)与并发性(对尽可能短的代码路径进行同步)之间的平衡。
 * 在获取与释放锁等操作上都需要一定的开销，因此如果将同步代码块分解得过细(例如将++hits分解到它自己的同步代码块中)，
 * 那么通常并不好，尽管这样做不会破坏原子性。当访问状态变量或者在复合操作的执行期间，CachedFactorizer 需要持有锁，
 * 但在执行时间较长的因数分解运算之前要释放锁。这样既确保了线程安全性，也不会过多地影响并发性，而且在每个同步代码块中的代码路径都“足够短”。
 *
 * 要判断同步代码块的合理大小，需要在各种设计需求之间进行权衡，包括安全性(这个需求必须得到满足)、简单性和性能。
 * 有时候，在简单性与性能之间会发生冲突，但在CachedFactorizer中已经说明了，在二者之间通常能找到某种合理的平衡。
 *
 */
@RestController("/CachedFactorizer")
@ThreadSafe
@Recommend
public class CachedFactorizer {

    private  Long lastNumber;

    private  List<Long> lastFactors = new ArrayList<>();

    private long hits;

    private long cacheHist;

    public synchronized long getHist(){
        return hits;
    }

    public synchronized double getCacheHitRatio(){
        return (double) cacheHist / (double) hits;
    }

    @GetMapping("/service")
    public synchronized List<Long> service(@RequestParam(required = false,name = "value") Long value){
        List<Long> factors = null;
        synchronized (this){
            ++hits;
            if (value.equals(lastNumber)) {
                ++cacheHist;
                factors = new ArrayList<>(lastFactors);
            }
        }
        if (factors == null) {
            factors = getAllFactors(value);
            synchronized (this){
                lastNumber = value;
                lastFactors = new ArrayList<>(factors);
            }
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
