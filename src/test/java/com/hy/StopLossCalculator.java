package com.hy;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class StopLossCalculator {
    public enum Direction {
        LONG,   // 做多
        SHORT   // 做空
    }

    /**
     * 计算止损触发价格
     *
     * @param avgEntryPrice 当前持仓均价（盈亏平衡价）
     * @param positionQty   当前持仓数量（正数）
     * @param targetLoss    最大允许亏损金额（正数）
     * @param direction     交易方向（做多/做空）
     * @return 触发止损的目标价格
     */
    public static BigDecimal calculateStopLossPrice(BigDecimal avgEntryPrice,
                                                    BigDecimal positionQty,
                                                    BigDecimal targetLoss,
                                                    Direction direction, int newScale) {
        if (positionQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("持仓数量必须大于0");
        }
        if (targetLoss.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("目标亏损必须为正数");
        }

        // 每单位的亏损额度
        BigDecimal perUnitLoss = targetLoss.divide(positionQty, 10, RoundingMode.HALF_UP);

        BigDecimal stopLossPrice;
        if (direction == Direction.LONG) {
            // 做多：价格下跌会亏损
            stopLossPrice = avgEntryPrice.subtract(perUnitLoss);
        } else {
            // 做空：价格上涨会亏损
            stopLossPrice = avgEntryPrice.add(perUnitLoss);
        }

        return stopLossPrice.setScale(newScale, RoundingMode.HALF_UP);
    }

    public static void main(String[] args) {
        BigDecimal avgEntryPrice = new BigDecimal("4623.93"); // 当前盈亏平衡价
        BigDecimal positionQty = new BigDecimal("0.5");        // 持仓数量
        BigDecimal targetLoss = new BigDecimal("100");       // 允许亏损（正数）

        BigDecimal stopLossLong = calculateStopLossPrice(avgEntryPrice, positionQty, targetLoss, Direction.LONG, 2);
        BigDecimal stopLossShort = calculateStopLossPrice(avgEntryPrice, positionQty, targetLoss, Direction.SHORT, 2);

        System.out.println("做多止损触发价: " + stopLossLong);   // 99900.00
        System.out.println("做空止损触发价: " + stopLossShort); // 100100.00
    }
}
