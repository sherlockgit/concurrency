package com.sherlock.concurrency.chapter08.detailed_08_18;

import com.sherlock.concurrency.chapter08.detailed_08_13.Puzzle;
import com.sherlock.concurrency.chapter08.detailed_08_14.PuzzleNode;
import com.sherlock.concurrency.chapter08.detailed_08_16.ConcurrentPuzzleSolver;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可以识别无解情况的并发拼图求解器。
 *
 * <p>这是《Java 并发编程实战》中的 8.18。</p>
 *
 * <p>8.16 的 {@link ConcurrentPuzzleSolver} 有一个重要缺陷：
 * 如果搜索空间被全部探索完，仍然没有找到目标位置，
 * 调用 {@code solve()} 的线程会一直阻塞在 {@code solution.getValue()} 上。
 * 因为没人会调用 {@code solution.setValue(...)}。</p>
 *
 * <p>8.18 的修正思路是统计“当前仍然活着的搜索任务数量”：</p>
 *
 * <p>1. 每创建一个搜索任务，{@link #taskCount} 加一；</p>
 * <p>2. 每个搜索任务结束时，{@code taskCount} 减一；</p>
 * <p>3. 如果某个任务结束后发现 {@code taskCount == 0}，
 * 说明所有搜索任务都已经结束；</p>
 * <p>4. 如果此时还没有任何任务找到解，就把 {@code solution} 设置为 null，
 * 让等待中的 {@code solve()} 返回 null。</p>
 *
 * <p>注意：这里的 null 不是“找到的路径为空”，而是“没有解”。</p>
 *
 * @param <P> Position，拼图位置或问题状态类型
 * @param <M> Move，从一个位置移动到另一个位置的动作类型
 */
public class PuzzleSolver<P, M> extends ConcurrentPuzzleSolver<P, M> {

    /**
     * 当前已创建但尚未结束的搜索任务数量。
     *
     * <p>并发求解过程中，多个任务会同时创建子任务、同时结束，
     * 因此计数器必须是原子的。</p>
     */
    private final AtomicInteger taskCount = new AtomicInteger(0);

    public PuzzleSolver(Puzzle<P, M> puzzle) {
        super(puzzle);
    }

    /**
     * 创建带计数功能的搜索任务。
     *
     * <p>8.16 创建的是普通 {@code SolverTask}。
     * 8.18 覆盖这个工厂方法，改为创建 {@link CountingSolverTask}，
     * 从而在不重写搜索逻辑的情况下，给任务生命周期加上计数能力。</p>
     */
    @Override
    protected Runnable newTask(P position, M move, PuzzleNode<P, M> previous) {
        return new CountingSolverTask(position, move, previous);
    }

    /**
     * 带活动任务计数的搜索任务。
     *
     * <p>它仍然复用 8.16 中 {@link SolverTask#run()} 的搜索逻辑，
     * 只是在构造和结束时维护 {@link #taskCount}。</p>
     */
    protected class CountingSolverTask extends SolverTask {

        CountingSolverTask(P position, M move, PuzzleNode<P, M> previous) {
            super(position, move, previous);
            taskCount.incrementAndGet();
        }

        @Override
        public void run() {
            try {
                super.run();
            } finally {
                /*
                 * 如果当前任务结束后，系统中已经没有任何活动任务，
                 * 并且之前也没有任务找到解，那么搜索空间已经耗尽。
                 *
                 * ValueLatch 只接受第一次 setValue。
                 * 如果某个任务已经找到了真正的解，这里的 setValue(null)
                 * 会被忽略，不会覆盖已经找到的解。
                 */
                if (taskCount.decrementAndGet() == 0) {
                    solution.setValue(null);
                }
            }
        }
    }

    /**
     * 简单演示。
     *
     * <p>第一个例子有解：从 0 移动到 4。
     * 第二个例子无解：位置最多只能到 3，但目标是 4。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        PuzzleSolver<Integer, String> solvable =
                new PuzzleSolver<Integer, String>(new NumberLinePuzzle(4, 4));
        System.out.println("solvable: " + solvable.solve());

        PuzzleSolver<Integer, String> unsolvable =
                new PuzzleSolver<Integer, String>(new NumberLinePuzzle(3, 4));
        System.out.println("unsolvable: " + unsolvable.solve());
    }

    private static class NumberLinePuzzle implements Puzzle<Integer, String> {

        private static final String LEFT = "LEFT";
        private static final String RIGHT = "RIGHT";

        private final int maxPosition;
        private final int goal;

        private NumberLinePuzzle(int maxPosition, int goal) {
            this.maxPosition = maxPosition;
            this.goal = goal;
        }

        @Override
        public Integer initialPosition() {
            return 0;
        }

        @Override
        public boolean isGoal(Integer position) {
            return position == goal;
        }

        @Override
        public Set<String> legalMoves(Integer position) {
            Set<String> moves = new LinkedHashSet<String>();
            if (position > 0) {
                moves.add(LEFT);
            }
            if (position < maxPosition) {
                moves.add(RIGHT);
            }
            return moves;
        }

        @Override
        public Integer move(Integer position, String move) {
            if (LEFT.equals(move)) {
                return position - 1;
            }
            if (RIGHT.equals(move)) {
                return position + 1;
            }
            throw new IllegalArgumentException("unknown move: " + move);
        }
    }
}
