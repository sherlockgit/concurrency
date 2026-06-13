package com.sherlock.concurrency.chapter06.detailed_06_17;

import java.util.*;
import java.util.concurrent.*;

/**
 * 服务：从多家旅行公司获取报价，并在指定时间内返回排序后的结果。
 * 使用 ExecutorService.invokeAll 的限时版本，确保整体响应时间可控。
 * 如果某家公司超时或异常，则返回一个表示失败的报价（而不是丢弃或抛异常）。
 */
public class TravelQuoteRanker {
    private final ExecutorService exec = Executors.newCachedThreadPool();

    /**
     * 获取所有旅行公司的报价，在规定时间内完成，并按指定排序规则返回。
     * @param travelInfo  旅行信息（目的地、日期等）
     * @param companies   参与报价的公司集合
     * @param ranking     报价的排序规则（如价格升序）
     * @param time        最大等待时间
     * @param unit        时间单位
     * @return 排序后的报价列表（超时或异常的公司会返回一个代表错误的报价）
     * @throws InterruptedException 若当前线程在等待过程中被中断
     */
    public List<TravelQuote> getRankedTravelQuotes(
            TravelInfo travelInfo,
            Set<TravelCompany> companies,
            Comparator<TravelQuote> ranking,
            long time,
            TimeUnit unit) throws InterruptedException {

        // 1. 为每个公司创建一个 QuoteTask（负责调用该公司 API）
        List<QuoteTask> tasks = new ArrayList<>();
        for (TravelCompany company : companies) {
            tasks.add(new QuoteTask(company, travelInfo));
        }

        // 2. 将所有任务提交给线程池，并限时执行 invokeAll
        //    该方法在全部任务完成（或超时、或线程中断）时返回，每个任务对应的 Future 可能处于以下状态：
        //    - 正常完成：get() 返回 TravelQuote
        //    - 异常完成：get() 抛出 ExecutionException
        //    - 被取消（超时导致）：get() 抛出 CancellationException
        List<Future<TravelQuote>> futures = exec.invokeAll(tasks, time, unit);

        // 3. 收集结果，处理各种失败情况
        List<TravelQuote> quotes = new ArrayList<>(tasks.size());
        Iterator<QuoteTask> taskIter = tasks.iterator();

        for (Future<TravelQuote> f : futures) {
            QuoteTask task = taskIter.next();
            try {
                // 正常完成：获取报价
                quotes.add(f.get());
            } catch (ExecutionException e) {
                // 执行过程中抛出异常（如网络错误），将异常原因传递给任务对象，
                // 由任务生成一个“失败报价”
                quotes.add(task.getFailureQuote(e.getCause()));
            } catch (CancellationException e) {
                // 因超时或取消导致任务未完成，生成一个“超时报价”
                quotes.add(task.getTimeoutQuote(e));
            }
        }

        // 4. 按指定排序规则排序
        Collections.sort(quotes, ranking);
        return quotes;
    }

    /**
     * 内部任务类：负责从单个旅行公司获取报价。
     * 它保存了公司引用和旅行信息，并提供在失败时生成“占位报价”的方法。
     */
    private class QuoteTask implements Callable<TravelQuote> {
        private final TravelCompany company;
        private final TravelInfo travelInfo;

        public QuoteTask(TravelCompany company, TravelInfo travelInfo) {
            this.company = company;
            this.travelInfo = travelInfo;
        }

        @Override
        public TravelQuote call() throws Exception {
            // 调用远程服务获取实际报价（可能耗时、可能抛异常）
            return company.solicitQuote(travelInfo);
        }

        /**
         * 当任务因执行异常而失败时，返回一个代表失败状态的 TravelQuote。
         * @param cause 异常原因
         * @return 带有错误信息的报价对象
         */
        public TravelQuote getFailureQuote(Throwable cause) {
            return new TravelQuote(company, "FAILED: " + cause.getMessage());
        }

        /**
         * 当任务因超时或被取消而失败时，返回一个代表超时状态的 TravelQuote。
         * @param e 取消异常
         * @return 带有超时信息的报价对象
         */
        public TravelQuote getTimeoutQuote(CancellationException e) {
            return new TravelQuote(company, "TIMEOUT");
        }
    }

    // ------------------ 以下为示例依赖类（实际需替换为真实业务对象） ------------------
    static class TravelInfo {
        // 包含目的地、出发日期、人数等
    }

    static class TravelCompany {
        public TravelQuote solicitQuote(TravelInfo info) throws Exception {
            // 模拟实际调用：可能会 sleep 模拟网络，或抛出异常
            return new TravelQuote(this, "¥1000");
        }
    }

    static class TravelQuote implements Comparable<TravelQuote> {
        private final TravelCompany company;
        private final String price;

        public TravelQuote(TravelCompany company, String price) {
            this.company = company;
            this.price = price;
        }

        public String getPrice() { return price; }

        @Override
        public String toString() {
            return company + " -> " + price;
        }

        @Override
        public int compareTo(TravelQuote o) {
            return 0;
        }
        // 通常还会实现 getter/setter 和比较逻辑
    }
}
