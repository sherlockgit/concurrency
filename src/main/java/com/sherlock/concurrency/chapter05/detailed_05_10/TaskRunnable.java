package com.sherlock.concurrency.chapter05.detailed_05_10;


/**
 *  恢复中断状态以避免屏蔽中断
 * * 当在代码中调用了一个将抛出InterruptedException异常的方法时，你自己的方法也就变成了一个阻塞方法，
 * * 并且必须要处理对中断的响应。对于库代码来说，有两种基本选择:
 * * 传递InterruptedException。避开这个异常通常是最明智的策略-只需把InterruptedException传递给方法的调用者。
 * * 传递 InterruptedException的方法包括，根本不捕获该异常，或者捕获该异常，然后在执行某种简单的清理工作后再次抛出这个异常。
 * * 恢复中断。有时候不能抛出InterruptedException，例如当代码是Runnable的一部分时。
 * * 在这些情况下，必须捕获InterruptedException，并通过调用当前线程上的interrupt
 * * 方法恢复中断状态，这样在调用栈中更高层的代码将看到引发了一个中断
 */
public class TaskRunnable implements Runnable {


    @Override
    public void run() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
