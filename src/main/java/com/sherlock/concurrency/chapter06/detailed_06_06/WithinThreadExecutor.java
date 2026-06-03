package com.sherlock.concurrency.chapter06.detailed_06_06;

import java.util.concurrent.Executor;

/**
 * 在调用线程中以同步方式执行所有任务的Executor
 * *
 * 还可以编写一个Executor 使TaskExecutionWebServer 的行为类似于单线程的行为，即以同步的方式执行每个任务，
 * * 然后再返回，如程序WithinThreadExecutor 所示。
 */
public class WithinThreadExecutor implements Executor {
    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
