package com.sherlock.concurrency.chapter16.detailed_16_01;

import com.sherlock.concurrency.annoations.NotThreadSafe;

/**
 * 同步不足时可能出现令人意外的执行结果。
 *
 * <p>这是《Java 并发编程实战》中的 16.1。</p>
 *
 * <p>这个例子要说明的是：如果没有正确同步，
 * Java 程序中写在前面的语句，不一定会以你直觉中的顺序被另一个线程观察到。</p>
 *
 * <p>两个线程分别执行：</p>
 *
 * <p>线程 one：先写 a = 1，再读 b 到 x；</p>
 * <p>线程 other：先写 b = 1，再读 a 到 y。</p>
 *
 * <p>直觉上，你可能认为最终不可能出现 x == 0 且 y == 0，
 * 因为如果 one 先执行，那么 other 应该看到 a == 1；
 * 如果 other 先执行，那么 one 应该看到 b == 1。</p>
 *
 * <p>但在没有同步的情况下，编译器、处理器缓存、写缓冲区、指令重排序等因素
 * 都可能让两个线程都读到对方写入之前的旧值 0。</p>
 */
@NotThreadSafe
public class PossibleReordering {

    private static int x;
    private static int y;
    private static int a;
    private static int b;

    /**
     * 简单运行入口。
     *
     * <p>注意：这个程序演示的是“允许出现某些结果”，不是“必然复现某些结果”。
     * 是否能观察到 (0,0)，取决于 JVM、CPU、运行次数、机器负载等因素。</p>
     */
    public static void main(String[] args) throws Exception {
        final int attempts = 10_000;
        int zeroZero = 0;
        int zeroOne = 0;
        int oneZero = 0;
        int oneOne = 0;

        for (int i = 0; i < attempts; i++) {
            Result result = runOnce();

            if (result.x == 0 && result.y == 0) {
                zeroZero++;
            } else if (result.x == 0 && result.y == 1) {
                zeroOne++;
            } else if (result.x == 1 && result.y == 0) {
                oneZero++;
            } else if (result.x == 1 && result.y == 1) {
                oneOne++;
            } else {
                throw new AssertionError("unexpected result: " + result);
            }
        }

        System.out.println("16.1 possible reordering finished");
        System.out.println("attempts = " + attempts);
        System.out.println("(0,0) = " + zeroZero + "  <- allowed by data race, but not guaranteed to appear");
        System.out.println("(0,1) = " + zeroOne);
        System.out.println("(1,0) = " + oneZero);
        System.out.println("(1,1) = " + oneOne);
    }

    /**
     * 执行一次书中的竞态场景。
     */
    private static Result runOnce() throws InterruptedException {
        /*
         * 每次运行前都重置共享变量。
         * 这些变量都不是 volatile，也没有被同一把锁保护。
         */
        x = 0;
        y = 0;
        a = 0;
        b = 0;

        Thread one = new Thread(new Runnable() {
            @Override
            public void run() {
                a = 1;
                x = b;
            }
        }, "possible-reordering-one");

        Thread other = new Thread(new Runnable() {
            @Override
            public void run() {
                b = 1;
                y = a;
            }
        }, "possible-reordering-other");

        one.start();
        other.start();
        one.join();
        other.join();

        /*
         * join 只保证 main 线程能看到 one/other 已经完成后的结果。
         * 它不保证 one 和 other 之间的读写顺序。
         */
        return new Result(x, y);
    }

    /**
     * 保存一次运行结果。
     */
    private static class Result {
        final int x;
        final int y;

        Result(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }
    }
}
