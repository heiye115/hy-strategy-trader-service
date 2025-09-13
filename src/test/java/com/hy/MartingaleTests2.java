package com.hy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class MartingaleTests2 {

    public enum Direction {
        LONG, SHORT
    }

    public static class OrderLevel {
        public int index;                  // 第几单
        public BigDecimal price;           // 下单价格
        public BigDecimal margin;          // 本单保证金
        public BigDecimal cumulativeStep;  // 累计下跌/上涨百分比

        public OrderLevel(int index, BigDecimal price, BigDecimal margin, BigDecimal cumulativeStep) {
            this.index = index;
            this.price = price;
            this.margin = margin;
            this.cumulativeStep = cumulativeStep;
        }

        @Override
        public String toString() {
            return String.format("第%d单: 价格=%s, 保证金=%s, 累计幅度=%s%%",
                    index,
                    price.setScale(4, RoundingMode.HALF_UP),
                    margin.setScale(4, RoundingMode.HALF_UP),
                    cumulativeStep.multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP));
        }
    }

    /**
     * 生成马丁格尔每单价格和保证金序列（最大投入金额分配版）
     *
     * @param entryPrice       初始下单价
     * @param baseStep         每次跌/涨多少加仓（百分比，例如 1% = 0.01）
     * @param maxAddCount      最大加仓次数
     * @param amountMultiplier 加仓金额倍数
     * @param stepMultiplier   加仓价差倍数
     * @param leverage         杠杆倍数
     * @param maxTotalMargin   最大投入保证金
     * @param direction        多单 LONG / 空单 SHORT
     * @return 每单信息列表
     */
    public static List<OrderLevel> generateOrderPlanMaxMargin(
            BigDecimal entryPrice,
            BigDecimal baseStep,
            int maxAddCount,
            BigDecimal amountMultiplier,
            BigDecimal stepMultiplier,
            BigDecimal leverage,
            BigDecimal maxTotalMargin,
            Direction direction
    ) {
        List<OrderLevel> levels = new ArrayList<>();
        BigDecimal cumulativeStep = BigDecimal.ZERO;

        // 先计算未缩放的每单保证金（按倍数递增）
        List<BigDecimal> rawMargins = new ArrayList<>();
        BigDecimal totalRaw = BigDecimal.ZERO;
        for (int i = 0; i < maxAddCount; i++) {
            BigDecimal raw = amountMultiplier.pow(i);
            rawMargins.add(raw);
            totalRaw = totalRaw.add(raw);
        }

        // 计算缩放系数，使总投入不超过 maxTotalMargin
        BigDecimal scale = maxTotalMargin.divide(totalRaw, 16, RoundingMode.HALF_UP);

        for (int i = 0; i < maxAddCount; i++) {
            // 本次加仓保证金 = rawMargin * scale * leverage
            BigDecimal margin = rawMargins.get(i).multiply(scale).multiply(leverage);

            // 当前价差增量 = baseStep * (stepMultiplier ^ i)
            BigDecimal stepIncrement = baseStep.multiply(stepMultiplier.pow(i));

            // 累计下跌/上涨百分比
            cumulativeStep = cumulativeStep.add(stepIncrement);

            // 触发价格
            BigDecimal price;
            if (direction == Direction.LONG) {
                price = entryPrice.multiply(BigDecimal.ONE.subtract(cumulativeStep));
            } else {
                price = entryPrice.multiply(BigDecimal.ONE.add(cumulativeStep));
            }

            levels.add(new OrderLevel(i + 1, price, margin, cumulativeStep));
        }

        return levels;
    }

    // 测试
    public static void main(String[] args) {
        BigDecimal entryPrice = new BigDecimal("4725"); // 初始下单价
        BigDecimal baseStep = new BigDecimal("0.01");         // 1%
        BigDecimal amountMultiplier = new BigDecimal("1.1");  // 加仓金额倍数
        BigDecimal stepMultiplier = new BigDecimal("1.1");    // 加仓价差倍数
        BigDecimal leverage = new BigDecimal("100");            // 杠杆倍数
        BigDecimal maxTotalMargin = new BigDecimal("1000"); // 最大投入保证金
        int maxAddCount = 20;

        List<OrderLevel> plan = generateOrderPlanMaxMargin(
                entryPrice,
                baseStep,
                maxAddCount,
                amountMultiplier,
                stepMultiplier,
                leverage,
                maxTotalMargin,
                Direction.LONG
        );

        plan.forEach(System.out::println);

        // 输出总投入金额
        BigDecimal total = plan.stream()
                .map(l -> l.margin)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("总投入保证金=" + total.setScale(4, RoundingMode.HALF_UP));
    }
}
