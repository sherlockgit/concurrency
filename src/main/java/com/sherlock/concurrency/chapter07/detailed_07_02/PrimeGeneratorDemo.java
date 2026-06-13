package com.sherlock.concurrency.chapter07.detailed_07_02;

import com.sherlock.concurrency.chapter07.detailed_07_01.PrimeGenerator;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 使用 PrimeGenerator 在限定时间内生成素数。
 * 该方法启动一个素数生成线程，等待1秒后取消任务，并返回这1秒内生成的所有素数。
 * 这是一种典型的“限时运行”模式：让任务运行一段固定时间后优雅停止。
 */
public class PrimeGeneratorDemo {

    /**
     * 运行素数生成器1秒钟，返回期间生成的所有素数。
     * @return 1秒内生成的素数列表
     * @throws InterruptedException 若当前线程在休眠期间被中断
     */
    List<BigInteger> aSecondOfPrimes() throws InterruptedException {
        // 创建可取消的素数生成器
        PrimeGenerator generator = new PrimeGenerator();

        // 启动素数生成线程
        new Thread(generator).start();

        try {
            // 主线程休眠1秒（模拟时间预算）
            // 注意：实际代码中可以使用 TimeUnit.SECONDS.sleep(1)
            TimeUnit.SECONDS.sleep(1);
        } finally {
            // 无论是否发生异常（如中断），都取消生成器
            // 这确保了生成线程能够正常退出
            generator.cancel();
        }

        // 返回已生成的素数快照
        return generator.get();
    }
}
