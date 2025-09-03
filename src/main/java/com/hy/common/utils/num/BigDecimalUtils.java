package com.hy.common.utils.num;

import java.math.BigDecimal;

/**
 * BigDecimal工具类
 * 提供对BigDecimal的比较操作
 */
public class BigDecimalUtils {

    /**
     * 比较两个BigDecimal值
     *
     * @param v1  第一个值
     * @param opt 比较操作符
     * @param v2  第二个值
     * @return true 如果v1满足操作符与v2的比较条件，否则false
     */
    public static boolean compare(BigDecimal v1, Opt opt, BigDecimal v2) {
        if (v1 == null || v2 == null || opt == null) {
            throw new IllegalArgumentException("比较值或操作符不能为空");
        }
        return opt.compare(v1, v2);
    }

    /**
     * v1大于v2
     **/
    public static boolean gt(BigDecimal v1, BigDecimal v2) {
        return compare(v1, Opt.GT, v2);
    }

    /**
     * v1小于v2
     **/
    public static boolean lt(BigDecimal v1, BigDecimal v2) {
        return compare(v1, Opt.LT, v2);
    }

    /**
     * v1等于v2
     **/
    public static boolean eq(BigDecimal v1, BigDecimal v2) {
        return compare(v1, Opt.EQ, v2);
    }

    /**
     * v1大于等于v2
     **/
    public static boolean gte(BigDecimal v1, BigDecimal v2) {
        return compare(v1, Opt.GTEQ, v2);
    }

    /**
     * v1小于等于v2
     **/
    public static boolean lte(BigDecimal v1, BigDecimal v2) {
        return compare(v1, Opt.LTEQ, v2);
    }

    /**
     * v1不等于v2
     **/
    public static boolean ne(BigDecimal v1, BigDecimal v2) {
        return compare(v1, Opt.NE, v2);
    }

    /**
     * 比较操作符
     */
    public enum Opt {
        //大于
        GT {
            public boolean compare(BigDecimal v1, BigDecimal v2) {
                return v1.compareTo(v2) > 0;
            }
        },
        //小于
        LT {
            public boolean compare(BigDecimal v1, BigDecimal v2) {
                return v1.compareTo(v2) < 0;
            }
        },
        //等于
        EQ {
            public boolean compare(BigDecimal v1, BigDecimal v2) {
                return v1.compareTo(v2) == 0;
            }
        },
        //大于等于
        GTEQ {
            public boolean compare(BigDecimal v1, BigDecimal v2) {
                return v1.compareTo(v2) >= 0;
            }
        },
        //小于等于
        LTEQ {
            public boolean compare(BigDecimal v1, BigDecimal v2) {
                return v1.compareTo(v2) <= 0;
            }
        },
        //不等于
        NE {
            public boolean compare(BigDecimal v1, BigDecimal v2) {
                return v1.compareTo(v2) != 0;
            }
        };

        public abstract boolean compare(BigDecimal v1, BigDecimal v2);
    }

    public static void main(String[] args) {

    }
}

