package com.sherlock.concurrency.chapter05.detailed_05_15;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * 通过CyclicBarrier 协调细胞自动衍生系统中的计算
 ** *
 * 栅栏(Barrier)类似于闭锁，它能阻塞一组线程直到某个事件发生。栅栏与闭锁的关键区别在于，
 * * 所有线程必须同时到达栅栏位置，才能继续执行。闭锁用于等待事件，而栅栏用于等待其他线程。
 * * 栅栏用于实现一些协议，例如几个家庭决定在某个地方集合:“所有人6:00在麦当劳碰头，
 * * 到了以后要等其他人，之后再讨论下一步要做的事情。”
 *
 *CyclicBarrier 可以使一定数量的参与方反复地在栅栏位置汇集，它在并行迭代算法中非常有用:
 * * 这种算法通常将一个问题拆分成一系列相互独立的子问题。当线程到达栅栏位置时将调用await方法，
 * * 这个方法将阻塞直到所有线程都到达栅栏位置。如果所有线程都到达了栅栏位置，那么栅栏将打开，
 * * 此时所有线程都被释放，而栅栏将被重置以便下次使用。如果对await的调用超时，或者await
 * * 阻塞的线程被中断，那么栅栏就被认为是打破了，所有阻塞的await调用都将终止并抛出
 * * BrokenBarrierException。如果成功地通过栅栏，那么await 将为每个线程返回一个唯一
 * * 的到达索引号，我们可以利用这些索引来“选举”产生一个领导线程，并在下一次迭代中由该领导
 * * 线程执行一些特殊的工作。CyclicBarrier还可以使你将一个栅栏操作传递给构造函数，
 * * 这是一个Runnable，当成功通过栅栏时会(在一个子任务线程中)执行它，但在阻塞线程被释放之前是不能执行的。
 */
public class CellularAutomata {
    private final Board mainBoard;          // 主棋盘，用于存储全局状态
    private final CyclicBarrier barrier;    // 循环栅栏，使所有工作线程同步
    private final Worker[] workers;         // 工作线程数组

    /**
     * 构造函数，根据处理器核心数创建工作线程
     * @param board 初始棋盘
     */
    public CellularAutomata(Board board) {
        this.mainBoard = board;
        int count = Runtime.getRuntime().availableProcessors();  // 获取可用CPU核心数
        // 初始化栅栏，所有线程到达后执行一个合并操作（提交新值到主棋盘）
        this.barrier = new CyclicBarrier(count,
                new Runnable() {
                    public void run() {
                        // 当所有工作线程完成一轮计算后，由最后一个到达的线程调用此方法
                        mainBoard.commitNewValues();  // 将每个线程计算的局部新值提交到主棋盘
                    }
                });
        this.workers = new Worker[count];
        // 为每个核心分配一个子棋盘（将主棋盘划分为 count 块）
        for (int i = 0; i < count; i++)
            workers[i] = new Worker(mainBoard.getSubBoard(count, i));
    }

    // ---------- 内部类 Worker ----------
    private class Worker implements Runnable {
        private final Board board;   // 当前工作线程负责的子棋盘

        public Worker(Board board) {
            this.board = board;
        }

        @Override
        public void run() {
            // 不断迭代，直到子棋盘收敛（通常意味着整个系统收敛）
            while (!board.hasConverged()) {
                // 遍历子棋盘的每一个单元格，根据邻居计算新值
                for (int x = 0; x < board.getMaxX(); x++)
                    for (int y = 0; y < board.getMaxY(); y++)
                        board.setNewValue(x, y, computeValue(x, y));
                try {
                    // 等待其他工作线程完成本轮计算，所有线程到达栅栏后，
                    // 会自动执行栅栏的 Runnable 任务（提交新值）
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException ex) {
                    // 发生中断或栅栏损坏时，直接退出线程
                    return;
                }
            }
        }

        /**
         * 根据 (x, y) 位置的邻居计算下一时刻的值
         * 实际规则由具体的细胞自动机算法决定（如生命游戏、元胞自动机等）
         */
        private int computeValue(int x, int y) {
            // 这里只是一个占位实现，真实逻辑会访问 board 的邻居值进行计算
            // 例如：return board.getNeighborSum(x, y) % 2;
            return 0;
        }
    }

    // ---------- 公开方法 ----------
    /**
     * 启动所有工作线程，并等待整个计算过程收敛
     */
    public void start() {
        // 为每个 Worker 创建并启动一个线程
        for (int i = 0; i < workers.length; i++)
            new Thread(workers[i]).start();
        // 主线程等待收敛完成（具体实现依赖 Board 类，例如 while(!converged) sleep）
        mainBoard.waitForConvergence();
    }
}

/**
 * 棋盘接口（示意，实际需定义具体实现）
 * 包含获取子棋盘、提交新值、收敛判断等方法
 */
interface Board {
    /**
     * 获取一个子棋盘
     * @param totalParts 总分区数
     * @param partIndex  当前分区索引 (0..totalParts-1)
     * @return 子棋盘对象
     */
    Board getSubBoard(int totalParts, int partIndex);

    /**
     * 判断棋盘是否已收敛（值不再变化）
     */
    boolean hasConverged();

    /**
     * 获取棋盘在 X 方向的大小
     */
    int getMaxX();

    /**
     * 获取棋盘在 Y 方向的大小
     */
    int getMaxY();

    /**
     * 设置某个单元格的新值（暂存，不立即生效）
     */
    void setNewValue(int x, int y, int value);

    /**
     * 将所有暂存的新值提交为当前值（在一次栅栏同步后调用）
     */
    void commitNewValues();

    /**
     * 主线程调用，等待整个细胞自动机收敛
     */
    void waitForConvergence();
}
