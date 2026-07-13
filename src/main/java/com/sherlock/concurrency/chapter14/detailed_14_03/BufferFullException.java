package com.sherlock.concurrency.chapter14.detailed_14_03;

/**
 * 缓冲区已满异常。
 *
 * <p>14.3 中的 GrumpyBoundedBuffer 不会在缓冲区满时阻塞等待，
 * 而是通过这个异常告诉调用者：当前不能继续 put。</p>
 */
public class BufferFullException extends Exception {

    public BufferFullException() {
        super("buffer is full");
    }
}
