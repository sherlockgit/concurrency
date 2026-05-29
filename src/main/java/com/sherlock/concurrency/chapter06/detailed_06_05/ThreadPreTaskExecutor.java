package com.sherlock.concurrency.chapter06.detailed_06_05;

import java.util.concurrent.Executor;

/**
 * 为每个请求启动一个新线程的Executor
 * *
 * 在TaskExecutionWebServer 中，通过使用Executor，将请求处理任务的提交与任务
 * * 的实际执行解耦开来，并且只需采用另一种不同的Executor实现，就可以改变服务器的
 * * 行为。改变Executor实现或配置所带来的影响要远远小于改变任务提交方式带来的影响。
 * * 通常，Executor的配置是一次性的，因此在部署阶段可以完成，而提交任务的代码却会
 * * 不断地扩散到整个程序中，增加了修改的难度
 * 我可以容易地将 TaskExecutionWebServer 修改为类似 ThreadPerTaskWebServer
 * * 的行为，只需使用一个为每个请求都创建新线程的Executor。
 */
public class ThreadPreTaskExecutor implements Executor {
    @Override
    public void execute(Runnable command) {
        new Thread(command).start();
    }
}
