package com.sherlock.concurrency.chapter14.detailed_14_03;

/**
 * 缓冲区为空异常。
 *
 * <p>14.3 中的 GrumpyBoundedBuffer 不会在缓冲区空时阻塞等待，
 * 而是通过这个异常告诉调用者：当前不能继续 take。</p>
 */
public class BufferEmptyException extends Exception {

    public BufferEmptyException() {
        super("buffer is empty");
    }
}
