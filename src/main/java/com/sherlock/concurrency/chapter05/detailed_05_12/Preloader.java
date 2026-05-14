package com.sherlock.concurrency.chapter05.detailed_05_12;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * 使用FutureTask来提前加载稍后需要的数据
 * * *
 * FutureTask也可以用做闭锁。(FutureTask实现了 Future 语义，表示一种抽象的可生成结果的计算)。
 * * FutureTask表示的计算是通过 Callable来实现的，相当于一种可生成结果的Runnable，
 * * 并且可以处于以下3种状态:等待运行(Waiting to run)，正在运行Running)和运行完成(Completed)。
 * * “执行完成”表示计算的所有可能结束方式，包括正常结束、由于取消而结束和由于异常而结束等。
 * * 当FutureTask进入完成状态后，它会永远停止在这个状态上
 *
 * Future.get 的行为取决于任务的状态。如果任务已经完成，那么get 会立即返回结果，
 * * 否则get将阻塞直到任务进入完成状态，然后返回结果或者抛出异常。FutureTask
 * * 将计算结果从执行计算的线程传递到获取这个结果的线程，而FutureTask的规范确保了这种传递过程能实现结果的安全发布。
 */
public class Preloader {
    // 创建 FutureTask，封装加载产品信息的任务
    private final FutureTask<ProductInfo> future =
            new FutureTask<ProductInfo>(new Callable<ProductInfo>() {
                public ProductInfo call() throws DataLoadException {
                    // 实际加载产品信息的方法（可能抛出 DataLoadException）
                    return loadProductInfo();
                }
            });
    // 创建一个线程来执行 FutureTask
    private final Thread thread = new Thread(future);

    // 启动线程，开始异步加载
    public void start() {
        thread.start();
    }

    // 获取产品信息，如果尚未加载完成则阻塞等待
    public ProductInfo get() throws DataLoadException, InterruptedException {
        try {
            // 通过 FutureTask 的 get() 方法获取结果（可能阻塞）
            return future.get();
        } catch (ExecutionException e) {
            // 获取任务执行时抛出的原始异常
            Throwable cause = e.getCause();
            // 如果是预期的 DataLoadException，则重新抛出
            if (cause instanceof DataLoadException){
                throw (DataLoadException) cause;
            }
            return null;
        }
    }

    private ProductInfo loadProductInfo(){
        //加载产品信息
        return new ProductInfo();
    }
}
