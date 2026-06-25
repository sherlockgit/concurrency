package com.sherlock.concurrency.chapter07.detailed_07_25;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 未捕获异常日志处理器。
 *
 * <p>这是《Java 并发编程实战》中的 7.25，
 * 用来演示如何实现一个 {@link Thread.UncaughtExceptionHandler}：
 * 当线程因为未捕获异常而异常终止时，记录线程名和异常堆栈。</p>
 *
 * <p>这类处理器通常用于：</p>
 *
 * <p>1. 记录崩溃日志；</p>
 * <p>2. 上报监控系统；</p>
 * <p>3. 在后台线程意外失败时提供排查线索。</p>
 *
 * <p>它不负责恢复线程，也不负责重试任务，
 * 只负责把异常信息可靠地记下来。</p>
 */
public class UEHLogger implements Thread.UncaughtExceptionHandler {

    /**
     * 当线程因为未捕获异常而终止时被 JVM 调用。
     *
     * <p>这里使用匿名 Logger 把异常按严重级别写入日志系统。
     * 日志消息中包含线程名，便于定位到底是哪个工作线程崩溃了。</p>
     *
     * @param t 发生异常并终止的线程
     * @param e 未捕获异常
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        Logger logger = Logger.getAnonymousLogger();
        logger.log(Level.SEVERE, "Thread terminated with exception: " + t.getName(), e);
    }
}
