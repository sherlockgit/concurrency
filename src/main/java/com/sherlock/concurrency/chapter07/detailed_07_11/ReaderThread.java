package com.sherlock.concurrency.chapter07.detailed_07_11;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.FutureTask;

/**
 * ReaderThread。
 *
 * <p>这是《Java 并发编程实战》中的 7.11，用来说明：
 * 有些阻塞操作并不会按照“标准中断策略”来响应 {@link Thread#interrupt()}，
 * 例如某些传统的 socket I/O 读取操作可能会长时间阻塞在 {@link InputStream#read(byte[])} 上，
 * 即使线程已经被中断，也未必会立刻退出。</p>
 *
 * <p>这时就需要一种“非标准取消”手段。
 * 对于基于 Socket 的阻塞读操作，一个常见办法是直接关闭底层 socket。
 * 当 socket 被关闭后，阻塞中的 {@code read} 通常会抛出 {@link IOException}，
 * 从而使线程跳出阻塞并结束。</p>
 *
 * <p>因此，本示例通过重写 {@link #interrupt()}，
 * 把“关闭 socket”与“设置线程中断状态”封装到一起：
 * 外部调用者仍然只需要调用 {@code interrupt()}，
 * 但线程内部实际上采用了更适合 socket I/O 的取消方式。</p>
 */
public class ReaderThread extends Thread {

    /**
     * 每次读取数据时使用的缓冲区大小。
     */
    private static final int BUFSZ = 512;

    /**
     * 底层 socket。
     *
     * <p>之所以保留它的引用，是因为取消线程时需要显式关闭该 socket，
     * 从而打断阻塞中的 I/O 操作。</p>
     */
    private final Socket socket;

    /**
     * 与 socket 关联的输入流。
     *
     * <p>ReaderThread 的核心工作就是不断从该输入流中读取数据。</p>
     */
    private final InputStream in;

    /**
     * 根据 socket 构造一个 ReaderThread。
     *
     * @param socket 底层网络连接
     * @throws IOException 如果获取输入流失败
     */
    public ReaderThread(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
    }

    /**
     * 重新定义线程的中断行为。
     *
     * <p>标准的 {@code Thread.interrupt()} 只是设置中断标志位，
     * 但对于这里的 socket 阻塞读操作来说，仅设置中断标志通常还不够，
     * 因为 {@code read} 可能不会立刻因中断而返回。</p>
     *
     * <p>因此这里先关闭 socket，使阻塞中的读操作尽快失败并抛出 IOException；
     * 然后再调用 {@code super.interrupt()}，保留线程的中断语义。
     * 这样做的好处是：
     * 外部代码仍然使用统一的 {@code interrupt()} 作为取消入口，
     * 但内部可以根据具体阻塞机制执行更合适的取消动作。</p>
     */
    @Override
    public void interrupt() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 关闭失败时不再额外处理，继续执行 super.interrupt()
        } finally {
            super.interrupt();
        }
    }

    /**
     * 持续从 socket 中读取数据并处理。
     *
     * <p>当对端关闭连接时，{@code read} 会返回负数，线程正常结束；
     * 当外部线程调用 {@link #interrupt()} 并关闭 socket 时，
     * 阻塞中的 {@code read} 通常会抛出 {@link IOException}，线程也会结束。</p>
     */
    @Override
    public void run() {
        try {
            byte[] buf = new byte[BUFSZ];
            while (true) {
                int count = in.read(buf);
                if (count < 0) {
                    break;
                } else if (count > 0) {
                    processBuffer(buf, count);
                }
            }
        } catch (IOException e) {
            // 这里通常意味着：
            // 1. socket 被远端关闭；
            // 2. 当前线程被取消时，本端主动关闭了 socket；
            // 无论哪种情况，都允许线程直接退出。
        }
    }

    /**
     * 处理读到的数据。
     *
     * <p>书中的清单没有展开业务逻辑，这里保留为空方法，
     * 方便子类重写或后续根据需要扩展。</p>
     *
     * @param buf 读取缓冲区
     * @param count 本次读取到的有效字节数
     */
    public void processBuffer(byte[] buf, int count) {
    }
}
