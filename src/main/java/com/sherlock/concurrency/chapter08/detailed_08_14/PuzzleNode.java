package com.sherlock.concurrency.chapter08.detailed_08_14;

import java.util.LinkedList;
import java.util.List;

/**
 * 拼图求解框架中的链表节点。
 *
 * <p>这是《Java 并发编程实战》中的 8.14。</p>
 *
 * <p>8.13 的 {@code Puzzle} 描述“问题本身”：
 * 初始位置是什么、什么是目标位置、有哪些合法移动、移动后得到什么新位置。</p>
 *
 * <p>而 {@code PuzzleNode} 描述“搜索过程中的一个节点”：</p>
 *
 * <p>1. {@link #pos} 表示当前节点所在的位置；</p>
 * <p>2. {@link #move} 表示从上一个节点移动到当前位置时使用的动作；</p>
 * <p>3. {@link #prev} 指向上一个搜索节点。</p>
 *
 * <p>这三个字段连起来，就形成了一条从初始位置到当前位置的路径链。
 * 当某个节点已经是目标位置时，可以调用 {@link #asMoveList()}，
 * 沿着 {@code prev} 指针一路回溯，恢复出完整的移动步骤。</p>
 *
 * <p>这个类是不可变的：字段都是 final，并且构造后不再修改。
 * 不可变节点可以被顺序求解器和并发求解器安全共享。</p>
 *
 * @param <P> Position，拼图位置或问题状态类型
 * @param <M> Move，从一个位置移动到另一个位置的动作类型
 */
public class PuzzleNode<P, M> {

    /**
     * 当前节点对应的位置。
     */
    protected final P pos;

    /**
     * 从前一个节点移动到当前节点所使用的动作。
     *
     * <p>初始节点没有前一个节点，因此初始节点的 move 通常为 null。</p>
     */
    protected final M move;

    /**
     * 指向前一个搜索节点。
     *
     * <p>初始节点没有前驱，因此初始节点的 prev 通常为 null。</p>
     */
    protected final PuzzleNode<P, M> prev;

    /**
     * 创建一个搜索节点。
     *
     * @param pos 当前节点的位置
     * @param move 从前一个节点移动到当前位置时使用的动作
     * @param prev 前一个搜索节点
     */
    public PuzzleNode(P pos, M move, PuzzleNode<P, M> prev) {
        this.pos = pos;
        this.move = move;
        this.prev = prev;
    }

    /**
     * 返回当前节点所在的位置。
     */
    public P getPosition() {
        return pos;
    }

    /**
     * 返回从前一个节点移动到当前节点时使用的动作。
     */
    public M getMove() {
        return move;
    }

    /**
     * 返回前一个搜索节点。
     */
    public PuzzleNode<P, M> getPrevious() {
        return prev;
    }

    /**
     * 将从初始位置到当前节点的路径恢复成动作列表。
     *
     * <p>链表节点的方向是“当前节点 -> 前一个节点 -> 再前一个节点”，
     * 也就是从目标位置反向指回初始位置。
     * 但求解结果应该按正向顺序返回：
     * 第一步、第二步、第三步......直到目标。</p>
     *
     * <p>因此这里从当前节点开始向前回溯，
     * 每遇到一个非 null 的 move，就把它插入到列表头部。
     * 最后得到的列表就是从初始位置走到当前节点需要执行的动作序列。</p>
     *
     * @return 从初始位置到当前节点的动作列表
     */
    public List<M> asMoveList() {
        List<M> solution = new LinkedList<M>();
        for (PuzzleNode<P, M> node = this; node.move != null; node = node.prev) {
            solution.add(0, node.move);
        }
        return solution;
    }

    /**
     * 简单演示路径恢复。
     */
    public static void main(String[] args) {
        PuzzleNode<String, String> start = new PuzzleNode<String, String>("A", null, null);
        PuzzleNode<String, String> step1 = new PuzzleNode<String, String>("B", "A -> B", start);
        PuzzleNode<String, String> step2 = new PuzzleNode<String, String>("C", "B -> C", step1);

        System.out.println(step2.asMoveList());
    }
}
