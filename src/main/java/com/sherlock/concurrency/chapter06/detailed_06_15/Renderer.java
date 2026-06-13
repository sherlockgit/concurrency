package com.sherlock.concurrency.chapter06.detailed_06_15;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * 使用 CompletionService 实现页面渲染器。
 * CompletionService 将 Executor 和 BlockingQueue 结合起来，
 * 可以按照任务完成的先后顺序获取结果，从而实现“边下载边渲染”的效果。
 * 相比于 FutureRenderer 必须等待所有图片下载完才开始渲染，
 * 本方案提高了响应性：每当一张图片下载完成，就立即渲染它。
 */
public class Renderer {
    private final ExecutorService executor;

    public Renderer(ExecutorService executor) {
        this.executor = executor;
    }

    void renderPage(CharSequence source) {
        // 1. 扫描页面中所有的图片信息
        List<ImageInfo> info = scanForImageInfo(source);

        // 2. 创建 CompletionService，它包装了线程池
        CompletionService<ImageData> completionService =
                new ExecutorCompletionService<>(executor);

        // 3. 为每张图片提交一个独立的下载任务
        for (final ImageInfo imageInfo : info) {
            completionService.submit(new Callable<ImageData>() {
                public ImageData call() {
                    // 每张图片独立下载（可能通过网络）
                    return imageInfo.downloadImage();
                }
            });
        }

        // 4. 在后台下载图片的同时，主线程先渲染文本部分
        renderText(source);

        // 5. 按照图片完成的顺序依次获取结果并渲染
        try {
            for (int t = 0, n = info.size(); t < n; t++) {
                // take() 会阻塞直到有任务完成，并返回该任务的 Future
                Future<ImageData> f = completionService.take();
                // 获取已下载完成的图片数据（get() 此时不会阻塞，因为任务已完成）
                ImageData imageData = f.get();
                // 立即渲染该图片
                renderImage(imageData);
            }
        } catch (InterruptedException e) {
            // 当前线程在等待过程中被中断：重新设置中断状态
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            // 下载图片过程中发生了异常，将原因包装为运行时异常抛出
            throw launderThrowable(e.getCause());
        }
    }

    // ------------------ 以下为示例辅助方法（实际需具体实现） ------------------
    private List<ImageInfo> scanForImageInfo(CharSequence source) {
        // 解析源内容，收集所有图片信息（示例返回空列表）
        return null;
    }

    private void renderText(CharSequence source) {
        // 渲染文本的逻辑（省略）
    }

    private void renderImage(ImageData data) {
        // 将图片绘制到界面上（省略）
    }

    /**
     * 将检查型异常转换为运行时异常或 Error。
     * @param t 原始异常原因
     * @return 永远不会正常返回，总是抛出异常
     */
    private RuntimeException launderThrowable(Throwable t) {
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        else if (t instanceof Error)
            throw (Error) t;
        else
            throw new IllegalStateException("未检查的异常", t);
    }

    // ------------------ 示例内部类（代表图片信息和数据） ------------------
    private static class ImageInfo {
        public ImageData downloadImage() {
            // 模拟图片下载：网络 I/O，耗时操作
            return new ImageData();
        }
    }

    private static class ImageData {
        // 图片的二进制数据或缓冲图像
    }
}
