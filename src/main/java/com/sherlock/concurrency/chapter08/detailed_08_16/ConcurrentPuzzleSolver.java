package com.sherlock.concurrency.chapter08.detailed_08_16;

import com.sherlock.concurrency.chapter08.detailed_08_13.Puzzle;
import com.sherlock.concurrency.chapter08.detailed_08_14.PuzzleNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 并发版本的拼图求解器。
 *
 * <p>这是《Java 并发编程实战》中的 8.16。</p>
 *
 * <p>8.15 的顺序求解器使用深度优先搜索：
 * 当前分支没搜完之前，不会去搜其他分支。
 * 8.16 把每个后继位置都包装成一个独立任务提交给线程池，
 * 因此多个搜索分支可以同时向前推进。</p>
 *
 * <p>这个版本有三个并发关键点：</p>
 *
 * <p>1. 使用 {@link ConcurrentHashMap} 记录已经搜索过的位置，
 * 防止多个线程重复搜索同一个状态；</p>
 * <p>2. 使用 {@link ValueLatch} 保存第一个找到的解，
 * 调用 {@link #solve()} 的线程会阻塞等待这个结果；</p>
 * <p>3. 一旦找到解，后续任务会通过 {@link ValueLatch#isSet()} 尽快退出，
 * 避免继续浪费线程池资源。</p>
 *
 * <p>和书中的 8.16 一样，这个版本还不能识别“无解”的情况。
 * 如果搜索空间全部耗尽仍然没有找到目标，{@link #solve()} 可能一直等待。
 * 这个缺陷会在后续 8.18 中修正。</p>
 *
 * @param <P> Position，拼图位置或问题状态类型
 * @param <M> Move，从一个位置移动到另一个位置的动作类型
 */
public class ConcurrentPuzzleSolver<P, M> {

    /**
     * 被求解的拼图问题。
     */
    private final Puzzle<P, M> puzzle;

    /**
     * 执行搜索任务的线程池。
     */
    private final ExecutorService exec;

    /**
     * 已经搜索过的位置。
     *
     * <p>顺序版本可以使用普通 HashSet；
     * 并发版本中多个 SolverTask 会同时检查和写入 seen，
     * 所以必须使用线程安全的 ConcurrentMap。</p>
     */
    private final ConcurrentMap<P, Boolean> seen;

    /**
     * 保存第一个找到的解。
     *
     * <p>ValueLatch 类本身是书中 8.17。
     * 为了让 8.16 这个文件可以单独编译运行，这里先放一个私有等价实现。</p>
     */
    protected final ValueLatch<PuzzleNode<P, M>> solution =
            new ValueLatch<PuzzleNode<P, M>>();

    public ConcurrentPuzzleSolver(Puzzle<P, M> puzzle) {
        this.puzzle = puzzle;
        this.exec = initThreadPool();
        this.seen = new ConcurrentHashMap<P, Boolean>();

        /*
         * 找到解以后 solve 会 shutdown 线程池。
         * 此时仍可能有一些已经开始运行的任务继续尝试提交后继任务。
         * 使用 DiscardPolicy 可以让这些迟到的提交被安静丢弃，
         * 避免因为线程池关闭而抛出 RejectedExecutionException。
         */
        if (exec instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor tpe = (ThreadPoolExecutor) exec;
            tpe.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        }
    }

    private ExecutorService initThreadPool() {
        return Executors.newCachedThreadPool();
    }

    /**
     * 启动并发搜索，并等待第一个解。
     *
     * <p>这个方法只提交初始位置对应的第一个任务。
     * 后续任务由每个 {@link SolverTask} 在搜索过程中继续派生出来。</p>
     *
     * @return 到达目标位置所需的动作列表；如果 ValueLatch 被设置为 null，则返回 null
     * @throws InterruptedException 当前线程等待解时被中断
     */
    public List<M> solve() throws InterruptedException {
        try {
            P position = puzzle.initialPosition();
            exec.execute(newTask(position, null, null));

            // 阻塞等待任意一个搜索任务找到解并设置到 solution 中。
            PuzzleNode<P, M> solutionNode = solution.getValue();
            return solutionNode == null ? null : solutionNode.asMoveList();
        } finally {
            exec.shutdown();
        }
    }

    /**
     * 创建一个搜索任务。
     *
     * <p>后续 8.18 会通过覆盖这个方法来统计活动任务数量，
     * 从而识别“所有任务都结束但仍然没有找到解”的情况。</p>
     */
    protected Runnable newTask(P position, M move, PuzzleNode<P, M> previous) {
        return new SolverTask(position, move, previous);
    }

    /**
     * 搜索任务。
     *
     * <p>这个类同时扮演两个角色：</p>
     *
     * <p>1. 它继承 {@link PuzzleNode}，表示搜索路径中的一个节点；</p>
     * <p>2. 它实现 {@link Runnable}，表示可以提交给线程池执行的搜索任务。</p>
     */
    protected class SolverTask extends PuzzleNode<P, M> implements Runnable {

        SolverTask(P position, M move, PuzzleNode<P, M> previous) {
            super(position, move, previous);
        }

        @Override
        public void run() {
            /*
             * 如果已经有人找到解，当前任务就不用继续搜索。
             *
             * putIfAbsent 是并发版本的关键：
             * 多个线程可能同时到达同一个 position，
             * 只有第一个线程能成功把它放入 seen，
             * 其他线程会得到非 null 返回值并直接退出。
             */
            if (solution.isSet() || seen.putIfAbsent(pos, true) != null) {
                return;
            }

            if (puzzle.isGoal(pos)) {
                solution.setValue(this);
            } else {
                for (M move : puzzle.legalMoves(pos)) {
                    P nextPosition = puzzle.move(pos, move);
                    exec.execute(newTask(nextPosition, move, this));
                }
            }
        }
    }

    /**
     * 一次性结果容器。
     *
     * <p>这是 8.17 ValueLatch 的简化内嵌版本：
     * 第一个调用 {@link #setValue(Object)} 的线程会设置结果并唤醒等待者；
     * 后续设置会被忽略。</p>
     *
     * @param <T> 结果类型
     */
    private static class ValueLatch<T> {

        private T value;

        private final CountDownLatch done = new CountDownLatch(1);

        public boolean isSet() {
            return done.getCount() == 0;
        }

        public synchronized void setValue(T newValue) {
            if (!isSet()) {
                value = newValue;
                done.countDown();
            }
        }

        public T getValue() throws InterruptedException {
            done.await();
            synchronized (this) {
                return value;
            }
        }
    }

    /**
     * 简单演示。
     *
     * <p>这个拼图和 8.15 的演示一致：从数字 0 出发，目标是 4。
     * 每一步可以向 LEFT 或 RIGHT 移动，位置范围限制在 0 到 4。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        ConcurrentPuzzleSolver<Integer, String> solver =
                new ConcurrentPuzzleSolver<Integer, String>(new NumberLinePuzzle());

        System.out.println(solver.solve());
    }

    private static class NumberLinePuzzle implements Puzzle<Integer, String> {

        private static final String LEFT = "LEFT";
        private static final String RIGHT = "RIGHT";

        @Override
        public Integer initialPosition() {
            return 0;
        }

        @Override
        public boolean isGoal(Integer position) {
            return position == 4;
        }

        @Override
        public Set<String> legalMoves(Integer position) {
            Set<String> moves = new LinkedHashSet<String>();
            if (position > 0) {
                moves.add(LEFT);
            }
            if (position < 4) {
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
