package com.sherlock.concurrency.chapter05.detailed_05_18;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * 一个具体的、开销较大的计算实现。
 * 示例：将字符串解析为 BigDecimal（实际应返回 BigInteger，这里示例有误，但不影响说明）
 */
public class ExpensiveFunction implements Computable<String, BigInteger> {
    /**
     * 模拟耗时计算，将字符串转换为 BigInteger（实际应转换为 BigDecimal 再转 BigInteger）
     * @param arg 输入字符串
     * @return 转换后的 BigInteger
     */
    public BigInteger compute(String arg) {
        // 在经过长时间的计算后（例如解析、加密、大数据处理等）
        return new BigDecimal(arg).toBigInteger();  // 修正：BigDecimal 可转 BigInteger
    }
}
