package com.sherlock.concurrency.chapter07.detailed_07_24;

/**
 * Thread.UncaughtExceptionHandler 演示。
 *
 * <p>这个示例演示三层顺序：</p>
 * <p>1. 线程自身的未捕获异常处理器；</p>
 * <p>2. 全局默认未捕获异常处理器；</p>
 * <p>3. 未设置线程处理器时的回退行为。</p>
 */
public class UncaughtExceptionHandlerDemo {

    public static void main(String[] args) throws InterruptedException {
        // 设置全局默认处理器：当线程自己没有指定处理器时，会回退到这里
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                System.err.println("default handler -> " + t.getName() + ": " + e.getMessage());
            }
        });

        Thread threadWithOwnHandler = new Thread(new Runnable() {
            @Override
            public void run() {
                throw new RuntimeException("thread-specific failure");
            }
        }, "with-own-handler");

        threadWithOwnHandler.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                System.err.println("thread handler -> " + t.getName() + ": " + e.getMessage());
            }
        });

        Thread threadUseDefaultHandler = new Thread(new Runnable() {
            @Override
            public void run() {
                throw new RuntimeException("default handler failure");
            }
        }, "use-default-handler");

        threadWithOwnHandler.start();
        threadUseDefaultHandler.start();

        threadWithOwnHandler.join();
        threadUseDefaultHandler.join();
    }
}
