package com.sherlock.concurrency.chapter07.detailed_07_08;

import com.sherlock.concurrency.annoations.NotRecommend;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 在借用来的线程上安排一次中断。
 *
 * <p>这是《Java 并发编程实战》中的 7.8，也是一个“不推荐”的示例。
 * 它试图实现“让一个任务只运行指定时长”的功能：
 * 当前线程调用 {@link #timedRun(Runnable, long, TimeUnit)} 时，
 * 会先安排一个定时任务，在超时后去中断“当前线程”，然后直接在当前线程中执行 {@code r.run()}。</p>
 *
 * <p>表面上看，这似乎可以达到“超时就中断任务”的目的，
 * 但它存在一个严重问题：这里被中断的并不是一个专门为任务创建的线程，
 * 而是调用方当前正在使用的线程，也就是一本书中所说的“借用来的线程（borrowed thread）”。</p>
 *
 * <p>这会带来两个风险：</p>
 *
 * <p>1. 如果 {@code r.run()} 在超时之前就已经结束，
 * 那么预先安排的中断任务仍然可能在稍后某个时刻执行。
 * 这时被中断的就不再是“原来的任务”，而是这个调用线程后续正在做的其他工作，
 * 从而把中断错误地传递给了无关代码。</p>
 *
 * <p>2. 如果 {@code r.run()} 完全不响应中断，
 * 那么即使定时器线程按时调用了 {@link Thread#interrupt()}，
 * 也无法保证当前任务能在超时后立刻停止。
 * 也就是说，这段代码既可能“误伤后续逻辑”，也不能可靠地停止目标任务。</p>
 *
 * <p>因此，7.8 的作用主要是展示一种错误思路：
 * 不要在借用来的线程上安排延迟中断。
 * 正确做法通常是像 7.9、7.10 那样，把任务放到专门线程或 Future 中执行并取消。</p>
 */
@NotRecommend
public class TimedRun1 {

    /**
     * 单线程定时调度器，用来在超时后发送中断信号。
     *
     * <p>这里使用静态线程池，表示所有 timedRun 调用共享同一个“取消调度器”。</p>
     */
    private static final ScheduledExecutorService cancelExec = Executors.newScheduledThreadPool(1);

    /**
     * 在限定时间内运行一个任务。
     *
     * <p>实现思路如下：</p>
     *
     * <p>1. 先记录当前线程，也就是“真正执行 r.run() 的线程”。</p>
     * <p>2. 向调度器提交一个延迟任务：到达超时时间后调用 {@code taskThread.interrupt()}。</p>
     * <p>3. 然后直接在当前线程中执行 {@code r.run()}。</p>
     *
     * <p>问题也恰恰出在第 3 步：
     * 由于任务并不是在一个受控的专用线程中执行，而是在调用者线程中执行，
     * 所以这个延迟中断实际上是针对“调用者线程”本身，而不仅仅是针对某个任务。</p>
     *
     * @param r 需要运行的任务
     * @param timeout 超时时长
     * @param unit 超时单位
     */
    public static void timedRun(Runnable r, long timeout, TimeUnit unit) {
        // 记录当前线程。后面超时后要中断的，就是这个线程。
        final Thread taskThread = Thread.currentThread();

        // 安排一个定时取消任务：超时后去中断当前线程。
        cancelExec.schedule(new Runnable() {
            @Override
            public void run() {
                taskThread.interrupt();
            }
        }, timeout, unit);

        // 直接在调用方线程中运行任务。
        // 这正是该实现不安全的根源：调用方线程可能在 r.run() 返回后继续执行其他工作，
        // 但之前安排的延迟中断却仍然可能在稍后触发。
        r.run();
    }
}
