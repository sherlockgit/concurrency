package com.sherlock.concurrency.chapter06.detailed_06_13;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 使用Future等待图像下载。
 * 该类在渲染文本的同时，通过 Callable 任务异步下载所有图片，
 * 最后通过 Future.get() 获取下载结果并渲染图片。
 *
 * 优点：文本渲染和图片下载可以并行进行，减少了总响应时间。
 * 缺点：仍然需要等待所有图片下载完成才能开始渲染图片，
 *       不能边下载边渲染。
 */
public class FutureRenderer {
    // 线程池（具体配置在构造函数或初始化块中，例如 Executors.newCachedThreadPool()）
    private final ExecutorService executor = Executors.newCachedThreadPool();  // 假设已初始化

    /**
     * 渲染页面：先异步下载图片，同时渲染文本，最后等待图片下载完成并渲染图片。
     * @param source 页面源内容（如 HTML）
     */
    void renderPage(CharSequence source) {
        // 1. 扫描页面中所有的图片信息（快速，不涉及网络 I/O）
        final List<ImageInfo> imageInfos = scanForImageInfo(source);

        // 2. 创建一个 Callable 任务，用于下载所有图片
        Callable<List<ImageData>> task = new Callable<List<ImageData>>() {
            public List<ImageData> call() {
                List<ImageData> result = new ArrayList<ImageData>();
                for (ImageInfo imageInfo : imageInfos) {
                    // 同步下载单个图片（阻塞），但不同图片之间串行执行
                    result.add(imageInfo.downloadImage());
                }
                return result;
            }
        };
        // 3. 将任务提交给线程池，返回 Future 对象代表异步计算
        Future<List<ImageData>> future = executor.submit(task);
        // 4. 在图片下载的同时，主线程继续渲染文本部分（不依赖图片）
        renderText(source);

        // 5. 尝试获取下载结果：如果尚未完成则阻塞等待
        try {
            List<ImageData> imageData = future.get(); // 可能抛出 InterruptedException 或 ExecutionException
            // 6. 所有图片下载完成后，依次渲染图片
            for (ImageData data : imageData) {
                renderImage(data);
            }
        } catch (InterruptedException e) {
            // 当前线程在等待过程中被中断：重新设置中断状态，以便上层代码能响应中断
            Thread.currentThread().interrupt();
            // 由于不需要结果，取消尚未完成的任务（可能正在下载图片）
            future.cancel(true);
        } catch (ExecutionException e) {
            // 下载图片过程中发生了异常（例如网络错误），包装为运行时异常抛出
            throw launderThrowable(e.getCause());
        }
    }

    // ------------------ 以下为示例辅助方法（实际需具体实现） ------------------
    private List<ImageInfo> scanForImageInfo(CharSequence source) {
        // 解析源内容，收集所有图片信息（示例返回空列表）
        return new ArrayList<>();
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
