package com.sherlock.concurrency.chapter06.detailed_06_09;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * 错误的Timer行为
 * *
 * 演示 java.util.Timer 的缺陷：当 TimerTask 抛出未捕获的异常时，
 * Timer 线程会终止，导致所有已调度但未执行的任务被丢弃，并且无法恢复。
 *
 * 运行此类会看到：
 * - 第一个任务抛出 RuntimeException，Timer 线程崩溃
 * - 第二个任务永远不会执行（因为 Timer 已死）
 * - 程序可能会提前退出或等待一段时间后结束
 *
 * * *
 * Timer类负责管理延迟任务(“在100ms 后执行该任务”)以及周期任务(“每10ms执行一次该任务”)。
 * * 然而，Timer 存在一些缺陷，因此应该考虑使用 ScheduledThreadPoolExecutor 来代替它。
 * * 可以通过 ScheduledThreadPoolExecutor 的构造函数或 newScheduledThreadPool
 * * 工厂方法来创建该类的对象。
 *
 * Timer在执行所有定时任务时只会创建一个线程。如果某个任务的执行时间过长，那么将破坏其他
 * * TimerTask的定时精确性。例如某个周期TimerTask需要每10ms执行一次，而另一个TimerTask
 * * 需要执行40ms，那么这个周期任务或者在40ms 任务执行完成后快速连续地调用4次，或者彻底“丢失”
 * * 4次调用(取决于它是基于固定速率来调度还是基于固定延时来调度)。线程池能弥补这个缺陷，它可以
 * * 提供多个线程来执行延时任务和周期任务。
 *
 * Timer的另一个问题是，如果TimerTask抛出了一个未检查的异常，那么Timer将表现出糟糕的行为。
 * * Timer线程并不捕获异常，因此当TimerTask抛出未检查的异常时将终止定时线程。这种情况下，
 * * Timer也不会恢复线程的执行，而是会错误地认为整个Timer都被取消了。因此，已经被调度但尚
 * * 未执行的TimerTask将不会再执行，新的任务也不能被调度。(这个问题称之为“线程泄漏[Thread Leakage]”)
 */
public class OutOfTime {
    public static void main(String[] args) throws Exception {
        // 创建一个 Timer（内部包含一个单线程的后台线程）
        Timer timer = new Timer();

        // 调度第一个任务：延迟1毫秒后执行，该任务会抛出 RuntimeException
        timer.schedule(new ThrowTask(), 1);

        // 等待1秒钟（让第一个任务有机会执行并杀死 Timer 线程）
        TimeUnit.SECONDS.sleep(1);

        // 尝试调度第二个任务：同样延迟1毫秒执行
        // 但因为 Timer 线程已经因异常而死亡，这个任务永远不会执行
        // 甚至不会抛出任何异常来通知调用者
        timer.schedule(new ThrowTask(), 1);

        // 再等待5秒钟，观察第二个任务是否会执行（实际上不会）
        TimeUnit.SECONDS.sleep(5);
    }

    /**
     * 一个故意抛出运行时异常的 TimerTask。
     */
    static class ThrowTask extends TimerTask {
        public void run() {
            // 抛出未捕获的 RuntimeException
            // 这会导致 Timer 线程终止（参见 Timer 的 Javadoc）
            throw new RuntimeException("TimerTask 抛出的致命异常");
        }
    }
}
