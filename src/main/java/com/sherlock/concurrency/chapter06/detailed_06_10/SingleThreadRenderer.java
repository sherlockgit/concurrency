package com.sherlock.concurrency.chapter06.detailed_06_10;

import java.util.ArrayList;
import java.util.List;

/**
 * 单线程页面渲染器。
 * 该类演示了最简单的顺序执行方式：先渲染文本，然后依次下载所有图片（阻塞式），
 * 最后再依次渲染图片。
 *
 * 缺点：
 * - 下载图片是 I/O 密集型操作，串行下载耗时很长，CPU 在此期间空闲。
 * - 渲染图片和下载图片无法重叠进行。
 * - 无法利用多核 CPU 或多线程加速。
 */
public class SingleThreadRenderer {

    /**
     * 渲染整个页面（文本 + 图片）
     * @param source 页面源内容（如 HTML 文本）
     */
    void renderPage(CharSequence source) {
        // 1. 渲染文本部分（假设是 CPU 密集型或 I/O 操作）
        renderText(source);

        // 2. 扫描页面中所有的图片信息（返回 ImageInfo 列表）
        //    这里 scanForImageInfo 可能是快速解析，不涉及实际下载
        List<ImageData> imageData = new ArrayList<ImageData>();
        for (ImageInfo imageInfo = scanForImageInfo(source);
             imageInfo != null;
             imageInfo = scanForImageInfo(source)) {
            // 3. 同步下载图片（阻塞，直到图片数据完全获取）
            //    每个图片下载完成后再下载下一个，串行执行
            imageData.add(imageInfo.downloadImage());
        }

        // 4. 所有图片下载完成后，依次渲染到屏幕上
        for (ImageData data : imageData) {
            renderImage(data);
        }
    }

    // ------------------ 以下为示例方法（实际需具体实现） ------------------
    private void renderText(CharSequence source) {
        // 渲染文本的逻辑（省略）
    }

    private ImageInfo scanForImageInfo(CharSequence source) {
        // 解析源内容，返回下一个图片信息，如果没有更多图片则返回 null
        return null;
    }

    private void renderImage(ImageData data) {
        // 将图片绘制到界面上（省略）
    }

    // 内部示例类
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
