package com.sherlock.concurrency.chapter08.detailed_08_15;

import com.sherlock.concurrency.chapter08.detailed_08_13.Puzzle;
import com.sherlock.concurrency.chapter08.detailed_08_14.PuzzleNode;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 顺序拼图求解器。
 *
 * <p>这是《Java 并发编程实战》中的 8.15。</p>
 *
 * <p>8.13 的 {@link Puzzle} 定义了“问题模型”，
 * 8.14 的 {@link PuzzleNode} 定义了“搜索路径节点”，
 * 本类则把它们组合起来，实现一个最基本的顺序搜索器。</p>
 *
 * <p>搜索思路是深度优先搜索：</p>
 *
 * <p>1. 从初始位置开始；</p>
 * <p>2. 如果当前位置已经访问过，直接跳过；</p>
 * <p>3. 如果当前位置是目标位置，返回从初始位置到当前位置的移动路径；</p>
 * <p>4. 否则枚举当前位置的所有合法移动，递归搜索每一个后继位置；</p>
 * <p>5. 如果所有分支都找不到目标，返回 null。</p>
 *
 * <p>这个版本完全没有并发逻辑，所有搜索都发生在调用 {@link #solve()} 的线程中。
 * 后续 8.16 会把搜索分支提交给线程池，从而变成并发求解器。</p>
 *
 * @param <P> Position，拼图位置或问题状态类型
 * @param <M> Move，从一个位置移动到另一个位置的动作类型
 */
public class SequentialPuzzleSolver<P, M> {

    /**
     * 被求解的拼图问题。
     */
    private final Puzzle<P, M> puzzle;

    /**
     * 已经访问过的位置集合。
     *
     * <p>搜索问题中经常会存在环，例如 A 可以移动到 B，B 又能移动回 A。
     * 如果不记录 seen，深度优先搜索可能在这些状态之间无限递归。</p>
     *
     * <p>这个类是顺序求解器，所以普通 {@link HashSet} 就够了。
     * 并发求解器中会改用线程安全的数据结构。</p>
     */
    private final Set<P> seen = new HashSet<P>();

    public SequentialPuzzleSolver(Puzzle<P, M> puzzle) {
        this.puzzle = puzzle;
    }

    /**
     * 从拼图的初始位置开始求解。
     *
     * <p>返回的是动作列表，而不是位置列表。
     * 例如返回 {@code [RIGHT, RIGHT]} 表示从初始位置开始连续执行两次 RIGHT 后到达目标。</p>
     *
     * <p>和书中示例一致，{@code seen} 是求解器实例的状态。
     * 因此一个 {@code SequentialPuzzleSolver} 实例更适合调用一次 {@code solve()}。
     * 如果要重新求解同一个问题，创建新的求解器实例更直接。</p>
     *
     * @return 到达目标位置所需的动作列表；如果找不到目标，返回 null
     */
    public List<M> solve() {
        P position = puzzle.initialPosition();
        return search(new PuzzleNode<P, M>(position, null, null));
    }

    /**
     * 从给定搜索节点开始进行深度优先搜索。
     *
     * <p>参数 node 不只是当前位置，它还保存了“如何走到当前位置”的整条路径。
     * 因此一旦发现 node 已经是目标，就可以通过 {@link PuzzleNode#asMoveList()}
     * 直接恢复出完整解。</p>
     *
     * @param node 当前搜索节点
     * @return 找到目标时返回动作列表；当前分支无解时返回 null
     */
    private List<M> search(PuzzleNode<P, M> node) {
        P position = node.getPosition();

        if (!seen.contains(position)) {
            seen.add(position);

            if (puzzle.isGoal(position)) {
                return node.asMoveList();
            }

            for (M move : puzzle.legalMoves(position)) {
                P nextPosition = puzzle.move(position, move);
                PuzzleNode<P, M> child =
                        new PuzzleNode<P, M>(nextPosition, move, node);

                List<M> result = search(child);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * 简单演示。
     *
     * <p>这个拼图把整数看成位置：从 0 出发，目标是 4。
     * 每一步可以 LEFT 或 RIGHT，但位置被限制在 0 到 4 之间。</p>
     */
    public static void main(String[] args) {
        SequentialPuzzleSolver<Integer, String> solver =
                new SequentialPuzzleSolver<Integer, String>(new NumberLinePuzzle());

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
