package com.hy.common.utils.num;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 金额计算器
 **/
public class AmountCalculator {

    public enum ChangeType {
        INCREASE, // 增长
        DECREASE  // 减少
    }

    /**
     * 根据百分比对本金进行增减
     *
     * @param principal  本金
     * @param percent    百分比 (例如 2 表示 2%)
     * @param changeType 增长 or 减少
     * @return 结果金额
     */
    public static BigDecimal applyChange(BigDecimal principal, BigDecimal percent, ChangeType changeType, int scale) {
        if (principal == null || percent == null || changeType == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        // 百分比转小数：2% -> 0.02
        BigDecimal rate = percent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);

        BigDecimal multiplier = (changeType == ChangeType.INCREASE)
                ? BigDecimal.ONE.add(rate)     // 增长：1 + 0.02 = 1.02
                : BigDecimal.ONE.subtract(rate); // 减少：1 - 0.02 = 0.98

        return principal.multiply(multiplier).setScale(scale, RoundingMode.HALF_UP); // 保留2位小数
    }

    /**
     * 根据百分比对本金进行增加
     *
     * @param principal 本金
     * @param percent   百分比 (例如 2 表示 2%)
     **/
    public static BigDecimal increase(BigDecimal principal, BigDecimal percent, int scale) {
        return applyChange(principal, percent, ChangeType.INCREASE, scale);
    }

    /**
     * 根据百分比对本金进行减少
     *
     * @param principal 本金
     * @param percent   百分比 (例如 2 表示 2%)
     **/
    public static BigDecimal decrease(BigDecimal principal, BigDecimal percent, int scale) {
        return applyChange(principal, percent, ChangeType.DECREASE, scale);
    }

    /**
     * 计算相对初始值的涨跌幅
     *
     * @param initial 初始值
     * @param current 当前值
     * @return 涨跌幅百分比（正数=上涨，负数=下跌）
     */
    public static BigDecimal calculateChangePercent(BigDecimal initial, BigDecimal current) {
        if (initial == null || current == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        if (initial.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("初始值不能为 0");
        }

        // (current - initial) / initial * 100
        BigDecimal diff = current.subtract(initial);
        return diff.divide(initial, 6, RoundingMode.HALF_UP) // 先算小数比率
                .multiply(BigDecimal.valueOf(100))        // 转百分比
                .setScale(2, RoundingMode.HALF_UP); // 保留2位小数
    }

    public static void main(String[] args) {
        BigDecimal principal = new BigDecimal("115400.1");
        BigDecimal percent = new BigDecimal("2");

        // 测试 applyChange
        BigDecimal increaseResult = applyChange(principal, percent, ChangeType.INCREASE, 2);
        BigDecimal decreaseResult = applyChange(principal, percent, ChangeType.DECREASE, 2);

        System.out.println("增长 2%: " + increaseResult); // 102.00
        System.out.println("减少 2%: " + decreaseResult); // 98.00

        // 测试 calculateChangePercent
        BigDecimal initial = new BigDecimal("100");
        BigDecimal current = new BigDecimal("90");
        BigDecimal changePercent = calculateChangePercent(initial, current);
        System.out.println("从100到150 涨跌幅: " + changePercent + "%"); // 50.00%
    }
}
