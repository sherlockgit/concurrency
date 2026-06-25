package com.sherlock.concurrency.chapter08.detailed_08_01;

import com.sherlock.concurrency.annoations.NotRecommend;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 单线程 Executor 中的任务饥饿死锁。
 *
 * <p>这是《Java 并发编程实战》中的 8.1。</p>
 *
 * <p>它想说明一个非常典型、也非常隐蔽的问题：
 * 某个任务自己运行在一个容量受限的线程池里，
 * 却又把“自己完成所依赖的子任务”提交回同一个线程池，
 * 随后立即等待这些子任务结果。</p>
 *
 * <p>如果线程池中没有足够空闲线程，父任务就会一直阻塞等待，
 * 而子任务又永远得不到执行机会，于是形成线程饥饿死锁
 * （thread-starvation deadlock）。</p>
 *
 * <p>8.1 官方代码是一个片段，这里补成完整可编译、可演示版本：</p>
 *
 * <p>1. {@link RenderPageTask} 负责渲染整个页面；</p>
 * <p>2. 它先把页眉、页脚渲染任务提交到同一个单线程 Executor；</p>
 * <p>3. 随后调用 {@link Future#get()} 等待结果；</p>
 * <p>4. 但单线程 Executor 当前唯一的工作线程正被父任务自己占着；</p>
 * <p>5. 因此页眉和页脚任务永远无法开始执行，页面渲染陷入死锁。</p>
 */
@NotRecommend
public class ThreadDeadlock {

    /**
     * 故意使用单线程线程池，以复现 8.1 的死锁场景。
     */
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    /**
     * 用内存中的模板内容代替真实文件，方便本地示例直接运行。
     */
    private final Map<String, String> pageParts = new HashMap<String, String>();

    public ThreadDeadlock() {
        pageParts.put("header.html", "<header>header</header>");
        pageParts.put("footer.html", "<footer>footer</footer>");
    }

    /**
     * 读取页面片段任务。
     *
     * <p>书中用“加载文件”来举例，这里保留原始语义，
     * 但把实际文件读取简化成从内存 Map 中取值。</p>
     */
    public class LoadFileTask implements Callable<String> {
        private final String fileName;

        public LoadFileTask(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public String call() {
            return readFile(fileName);
        }
    }

    /**
     * 页面渲染任务。
     *
     * <p>这就是 8.1 的核心错误写法：
     * 当前任务已经运行在 {@link #exec} 里，
     * 却又把依赖的页眉和页脚任务再次提交给同一个 {@link #exec}，
     * 然后同步等待结果。</p>
     */
    public class RenderPageTask implements Callable<String> {
        @Override
        public String call() throws Exception {
            Future<String> header = exec.submit(new LoadFileTask("header.html"));
            Future<String> footer = exec.submit(new LoadFileTask("footer.html"));

            String page = renderBody();

            return header.get() + page + footer.get();
        }
    }

    /**
     * 提交整个页面渲染任务。
     *
     * @return 页面渲染结果对应的 Future
     */
    public Future<String> renderPage() {
        return exec.submit(new RenderPageTask());
    }

    /**
     * 关闭示例线程池。
     */
    public void shutdownNow() {
        exec.shutdownNow();
    }

    /**
     * 读取“文件”内容。
     *
     * @param fileName 文件名
     * @return 模板内容
     */
    private String readFile(String fileName) {
        String content = pageParts.get(fileName);
        if (content == null) {
            throw new IllegalArgumentException("unknown file: " + fileName);
        }
        return content;
    }

    /**
     * 渲染页面主体。
     */
    private String renderBody() {
        return "<body>body</body>";
    }

    /**
     * 简单演示 8.1 的死锁现象。
     *
     * <p>为了避免示例在本地永久挂住，这里使用超时等待：
     * 如果 1 秒内仍拿不到结果，就说明页面渲染任务已经卡在死锁上。</p>
     */
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        ThreadDeadlock deadlock = new ThreadDeadlock();
        Future<String> page = deadlock.renderPage();

        try {
            System.out.println(page.get(1, TimeUnit.SECONDS));
        } catch (TimeoutException e) {
            System.out.println("8.1 演示结果：RenderPageTask 发生线程饥饿死锁，1 秒内未返回结果");
        } finally {
            deadlock.shutdownNow();
        }
    }
}
