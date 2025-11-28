package com.hy.common.utils.num;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class NumUtil {

    /**
     * 涨幅的计算公式为：涨幅=(今日收盘价-昨日收盘价)/昨日收盘价*100%，如果计算结果为负数，则为跌幅。
     **/
    public static BigDecimal increase(String v1, String v2) {
        return increase(new BigDecimal(v1), new BigDecimal(v2));
    }

    public static BigDecimal increase(BigDecimal v1, BigDecimal v2) {
        BigDecimal rs = v1.subtract(v2).divide(v2, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        return rs.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 根据总金额和单价计算购买量
     **/
    public static int countBuyVolume(BigDecimal unitPrice, BigDecimal totalAmount) {
        BigDecimal divide = totalAmount.divide(unitPrice, 2, RoundingMode.DOWN);
        int result = divide.setScale(-2, RoundingMode.DOWN).intValue();
        return Math.max(result, 100);
    }


    /**
     * 下跌区间
     **/
    public static void depreciateRange(String val, Integer num, Double decreasePercentage) {
        System.out.println("初始值:" + val);
        BigDecimal value = new BigDecimal(val);
        for (int i = 1; i <= num; i++) {
            BigDecimal rs = calculate(value, new BigDecimal(decreasePercentage));
            System.out.println("第" + i + "次: " + rs.toPlainString());
            value = new BigDecimal(rs.toPlainString());
        }
    }

    /**
     * 上涨区间
     **/
    public static void riseRange(String val, Integer num, Double decreasePercentage) {
        System.out.println("初始值:" + val);
        BigDecimal value = new BigDecimal(val);
        for (int i = 1; i <= num; i++) {
            BigDecimal rs = calculate(value, new BigDecimal(decreasePercentage));
            System.out.println("第" + i + "次: " + rs.toPlainString());
            value = new BigDecimal(rs.toPlainString());
        }
    }

    /**
     * 涨跌幅计算器
     * 100*(1+(2.5/100))
     * 100*(1+(-2.5/100))
     **/
    public static BigDecimal calculate(BigDecimal originalValue, BigDecimal percentage) {
        // 百分比除以100转换为小数
        BigDecimal divisor = new BigDecimal("100");
        BigDecimal percentageDecimal = percentage.divide(divisor, 10, RoundingMode.HALF_UP);
        // 计算 1 + 百分比小数
        BigDecimal factor = BigDecimal.ONE.add(percentageDecimal);
        // 原值乘以 (1 + 百分比小数)
        return originalValue.multiply(factor).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 根据涨跌幅计算可开最大杠杆  例如 5.0%  最大可以开杠杆是20 方法返回20整数 , 不要带有小数, 向下取整
     **/
    public static int calculateMaxLeverage(BigDecimal percentage) {
        if (percentage.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Percentage must be greater than zero.");
        }
        BigDecimal hundred = new BigDecimal("80");
        BigDecimal leverage = hundred.divide(percentage, 0, RoundingMode.DOWN);
        //如果杠杆小于1 则返回1
        if (leverage.compareTo(BigDecimal.ONE) < 0) {
            return 1;
        }
        return leverage.intValue();
    }

    /**
     * 计算交易所最大可开杠杆 如果calculateMaxLeverage返回大于交易所最大可开杠杆 则返回交易所最大杠杆, 否则返回calculateMaxLeverage的杠杆
     **/
    public static int calculateExchangeMaxLeverage(BigDecimal percentage, Integer exchangeMaxLeverage) {
        int calculatedLeverage = calculateMaxLeverage(percentage);
        return Math.min(calculatedLeverage, exchangeMaxLeverage);
    }

    public static void main(String[] args) {
        BigDecimal originalValue = new BigDecimal("100");
        BigDecimal percentageIncrease = new BigDecimal("2.5");
        BigDecimal percentageDecrease = new BigDecimal("-2.5");


        BigDecimal increasedValue = calculate(originalValue, percentageIncrease);
        BigDecimal decreasedValue = calculate(originalValue, percentageDecrease);

        System.out.println("Original value: " + originalValue);
        System.out.println("Value after 2.5% increase: " + increasedValue);
        System.out.println("Value after 2.5% decrease: " + decreasedValue);
        System.out.println(calculate(originalValue, new BigDecimal("0")));
        System.out.println(calculateMaxLeverage(BigDecimal.valueOf(1)));
        System.out.println(calculateExchangeMaxLeverage(BigDecimal.valueOf(5), 100));

    }

}
