package com.sherlock.concurrency.chapter06.detailed_06_16;

import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * 在指定时间预算内渲染页面，并尝试获取广告。
 * 如果广告下载超时或失败，则使用默认广告。
 * 这种方式确保页面主体渲染不会因广告延迟而被阻塞过久。
 */
public class PageRendererWithAd {
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private static final long TIME_BUDGET = 2000_000_000L; // 示例：2秒（纳秒）
    private static final Ad DEFAULT_AD = new Ad("默认广告");

    Page renderPageWithAd() throws InterruptedException {
        // 计算允许的截止时间（纳秒）
        long endNanos = System.nanoTime() + TIME_BUDGET;

        // 启动异步任务：获取广告
        Future<Ad> f = exec.submit(new FetchAdTask());

        // 在等待广告的同时，渲染页面主体（不依赖广告）
        Page page = renderPageBody();

        Ad ad;
        try {
            // 计算剩余可用时间
            long timeLeft = endNanos - System.nanoTime();
            // 在剩余时间内等待广告结果（若超时则抛出 TimeoutException）
            ad = f.get(timeLeft, NANOSECONDS);
        } catch (ExecutionException e) {
            // 广告获取过程中出现异常（如网络错误），使用默认广告
            ad = DEFAULT_AD;
        } catch (TimeoutException e) {
            // 广告下载超时，使用默认广告并取消正在进行的下载任务
            ad = DEFAULT_AD;
            f.cancel(true);   // 中断正在运行的 FetchAdTask 线程
        }

        // 将广告设置到页面中
        page.setAd(ad);
        return page;
    }

    // ------------------ 示例辅助方法（实际需具体实现） ------------------
    private Page renderPageBody() {
        // 渲染页面主体内容（文本、图片等）
        return new Page();
    }

    // 模拟获取广告的任务
    private static class FetchAdTask implements Callable<Ad> {
        @Override
        public Ad call() throws Exception {
            // 模拟网络请求获取广告（可能耗时较长）
            Thread.sleep(1500);  // 示例耗时
            return new Ad("精彩广告");
        }
    }

    // 页面类（示例）
    private static class Page {
        private Ad ad;
        public void setAd(Ad ad) { this.ad = ad; }
        // 其他页面内容...
    }

    // 广告类（示例）
    private static class Ad {
        private final String content;
        Ad(String content) { this.content = content; }
        // 广告内容...
    }
}
