package com.sherlock.concurrency.chapter08.detailed_08_13;

import java.util.Set;

/**
 * 拼图问题的通用抽象。
 *
 * <p>这是《Java 并发编程实战》中的 8.13。</p>
 *
 * <p>书里后面的 8.14 到 8.18 会基于这个接口实现一个“拼图求解器”框架。
 * 这里的“拼图”不一定只指滑块拼图，也可以理解为一类状态搜索问题：</p>
 *
 * <p>1. 有一个初始状态；</p>
 * <p>2. 可以判断某个状态是不是目标状态；</p>
 * <p>3. 在某个状态下，可以枚举所有合法动作；</p>
 * <p>4. 对状态执行某个动作后，可以得到下一个状态。</p>
 *
 * <p>泛型参数含义：</p>
 *
 * <p>{@code P} 表示 Position，也就是“状态”类型；</p>
 * <p>{@code M} 表示 Move，也就是“动作”类型。</p>
 *
 * <p>这个接口本身不包含并发逻辑。它只负责描述问题模型；
 * 是否顺序搜索、并发搜索、剪枝、去重，都是后续求解器的职责。</p>
 *
 * @param <P> 拼图位置或问题状态的类型
 * @param <M> 从一个位置移动到另一个位置的动作类型
 */
public interface Puzzle<P, M> {

    /**
     * 返回问题的初始位置。
     *
     * <p>求解器会从这个位置开始搜索。</p>
     *
     * @return 初始位置
     */
    P initialPosition();

    /**
     * 判断某个位置是否已经达到目标。
     *
     * <p>求解器每搜索到一个新位置，都会用这个方法判断是否已经找到答案。</p>
     *
     * @param position 要检查的位置
     * @return 如果 position 是目标位置，返回 true
     */
    boolean isGoal(P position);

    /**
     * 返回当前位置下可以执行的所有合法动作。
     *
     * <p>如果当前位置没有任何合法动作，返回空集合即可。
     * 调用方通常会遍历这些动作，并通过 {@link #move(Object, Object)}
     * 生成后续位置。</p>
     *
     * @param position 当前所在位置
     * @return 当前状态下的合法动作集合
     */
    Set<M> legalMoves(P position);

    /**
     * 在给定位置上执行一个动作，并返回执行后的新位置。
     *
     * <p>这个方法最好不要修改传入的 position 对象本身。
     * 对搜索算法来说，不可变位置对象更容易去重，也更容易安全地在多个线程之间共享。</p>
     *
     * @param position 当前所在位置
     * @param move 要执行的动作
     * @return 执行动作后的新位置
     */
    P move(P position, M move);
}
