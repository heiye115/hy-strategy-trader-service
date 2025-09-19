package com.hy.common.utils.num;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class CompoundCalculator {

    // 常量（避免二进制浮点误差）
    private static final BigDecimal POS_GROWTH = new BigDecimal("1.1"); // 仓位每轮 * 1.1
    private static final BigDecimal MARGIN_RATIO = new BigDecimal("0.9"); // 仓位 * 0.9 加入保证金
    private static final int PRINT_SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    // 计算轮数
    private static final int ROUNDS = 100;

    // 默认初始值
    private static final BigDecimal INIT_POS = new BigDecimal("100");

    // 仓位
    private static final BigDecimal INIT_MARGIN = new BigDecimal("500");

    /**
     * 生成复利表
     *
     * @param rounds     轮数（包含第 rounds 轮）
     * @param initPos    初始仓位
     * @param initMargin 初始保证金
     * @return List<CompoundRow>
     */
    public static List<CompoundRow> getCompoundTable(int rounds, BigDecimal initPos, BigDecimal initMargin) {
        if (rounds < 0) throw new IllegalArgumentException("rounds must be >= 0");
        if (initPos == null || initMargin == null)
            throw new IllegalArgumentException("initPos/initMargin cannot be null");

        List<CompoundRow> result = new ArrayList<>();
        BigDecimal position = initPos;
        BigDecimal margin = initMargin;

        for (int i = 0; i <= rounds; i++) {
            BigDecimal total = position.add(margin);

            CompoundRow row = new CompoundRow(
                    i,
                    scale(position),
                    scale(margin),
                    scale(total)
            );
            result.add(row);

            // 下一轮计算
            BigDecimal nextPosition = position.multiply(POS_GROWTH);
            BigDecimal nextMargin = margin.add(position.multiply(MARGIN_RATIO));

            position = nextPosition;
            margin = nextMargin;
        }

        return result;
    }

    /**
     * 保留两位小数
     */
    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(PRINT_SCALE, ROUNDING);
    }

    /**
     * 行数据封装类
     */
    @Getter
    @Setter
    @ToString
    @NoArgsConstructor
    public static class CompoundRow {

        private int round;         // 轮数
        private BigDecimal position; // 仓位
        private BigDecimal margin;   // 保证金
        private BigDecimal total;    // 总金额

        public CompoundRow(int round, BigDecimal position, BigDecimal margin, BigDecimal total) {
            this.round = round;
            this.position = position;
            this.margin = margin;
            this.total = total;
        }
    }


    /**
     * 根据现有账户总金额匹配对应的 CompoundRow
     * 规则：永远取小于等于 currentTotal 的那一档（floor 匹配）
     *
     * @param currentTotal 现有账户总金额
     * @param list         已计算的复利表
     * @return 匹配到的 CompoundRow
     */
    public static CompoundRow matchByTotal(BigDecimal currentTotal, List<CompoundRow> list) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("list cannot be null or empty");
        }
        if (currentTotal == null) {
            throw new IllegalArgumentException("currentTotal cannot be null");
        }

        // 如果小于等于第一条，返回第一条
        if (currentTotal.compareTo(list.getFirst().getTotal()) <= 0) {
            return list.getFirst();
        }

        // 如果大于等于最后一条，返回最后一条
        if (currentTotal.compareTo(list.getLast().getTotal()) >= 0) {
            return list.getLast();
        }

        // 遍历查找：找到第一个 total > currentTotal 的条目，返回它的前一个
        for (int i = 1; i < list.size(); i++) {
            if (currentTotal.compareTo(list.get(i).getTotal()) < 0) {
                return list.get(i - 1);
            }
        }
        // 理论上不会到这里
        return list.getLast();
    }

    /**
     * 获取复利计划
     **/
    public static CompoundRow getCompoundPlan(int rounds, BigDecimal initPos, BigDecimal initMargin, BigDecimal currentTotal) {
        List<CompoundRow> rows = getCompoundTable(rounds, initPos, initMargin);
        return matchByTotal(currentTotal, rows);
    }

    /**
     * 获取复利计划
     **/
    public static CompoundRow getCompoundPlan(BigDecimal currentTotal) {
        return matchByTotal(currentTotal, getCompoundTable(ROUNDS, INIT_POS, INIT_MARGIN));
    }

    // 测试
    public static void main(String[] args) {

        List<CompoundRow> rows = getCompoundTable(100, new BigDecimal("100"), new BigDecimal("500"));
        for (CompoundRow row : rows) {
            System.out.println(row.round + "," + row.position + "," + row.margin + "," + row.total);
        }
//
        BigDecimal current1 = new BigDecimal("671.62");
//        System.out.println(JsonUtil.toJson(matchByTotal(current1, rows)));
        CompoundRow plan = getCompoundPlan(current1);
        //System.out.println("当前总金额=" + current1 + "，对应计划：" + plan);
    }
}
