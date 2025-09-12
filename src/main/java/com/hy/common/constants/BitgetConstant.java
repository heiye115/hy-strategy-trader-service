package com.hy.common.constants;

public final class BitgetConstant {

    /**
     * 马丁账号名称
     **/
    public static final String MARTINGALE_ACCOUNT = "martingale";

    /**
     * 区间交易账号名称
     **/
    public static final String RANGE_TRADING_ACCOUNT = "range";

    /**
     * 默认币 USDT
     **/
    public static final String DEFAULT_CURRENCY_USDT = "USDT";


    /**
     * 产品类型
     * USDT-FUTURES USDT专业合约
     **/
    public static final String BG_PRODUCT_TYPE_USDT_FUTURES = "USDT-FUTURES";

    /**
     * 产品类型
     * COIN-FUTURES 混合合约
     **/
    public static final String BG_PRODUCT_TYPE_COIN_FUTURES = "COIN-FUTURES";

    /**
     * 产品类型
     * USDC-FUTURES USDC专业合约
     **/
    public static final String BG_PRODUCT_TYPE_USDC_FUTURES = "USDC-FUTURES";

    /**
     * 频道名
     **/
    public static final String BG_CHANNEL_TICKER = "ticker";

    /**
     * 持仓方向 long 多仓
     **/
    public static final String BG_HOLD_SIDE_LONG = "long";

    /**
     * 持仓方向 short 空仓
     **/
    public static final String BG_HOLD_SIDE_SHORT = "short";

    /**
     * 持仓模式 one_way_mode 单向持仓
     **/
    public static final String BG_POS_MODE_ONE_WAY_MODE = "one_way_mode";

    /**
     * 持仓模式 hedge_mode 双向持仓
     **/
    public static final String BG_POS_MODE_HEDGE_MODE = "hedge_mode";

    /**
     * 仓位模式  isolated 逐仓
     **/
    public static final String BG_MARGIN_MODE_ISOLATED = "isolated";

    /**
     * 仓位模式 crossed 全仓
     **/
    public static final String BG_MARGIN_MODE_CROSSED = "crossed";

    /**
     * 下单方向  buy 买
     **/
    public static final String BG_SIDE_BUY = "buy";

    /**
     * 下单方向 sell 卖
     **/
    public static final String BG_SIDE_SELL = "sell";


    /**
     * 交易方向 开平仓，双向持仓模式下必填  open 开
     **/
    public static final String BG_TRADE_SIDE_OPEN = "open";

    /**
     * 交易方向 开平仓，双向持仓模式下必填 close 平
     **/
    public static final String BG_TRADE_SIDE_CLOSE = "close";

    /**
     * 订单类型 limit 限价单，
     **/
    public static final String BG_ORDER_TYPE_LIMIT = "limit";

    /**
     * 订单类型 market 市价单
     **/
    public static final String BG_ORDER_TYPE_MARKET = "market";

    /**
     * 00000 表示接口执行成功
     **/
    public static final String BG_RESPONSE_CODE_SUCCESS = "00000";

    /**
     * 计划委托类型
     * normal_plan: 普通计划委托
     * track_plan: 追踪委托
     * profit_loss: 止盈止损类委托(包含了：profit_plan：止盈计划, loss_plan：止损计划, moving_plan：移动止盈止损，pos_profit：仓位止盈，pos_loss：仓位止损)
     **/
    public static final String BG_PLAN_TYPE_PROFIT_LOSS = "profit_loss";

    /**
     * 计划委托类型
     * pos_loss：仓位止损
     **/
    public static final String BG_PLAN_TYPE_POS_LOSS = "pos_loss";

    /**
     * 计划委托类型
     * pos_profit：仓位止盈
     **/
    public static final String BG_PLAN_TYPE_POS_PROFIT = "pos_profit";

    /**
     * 计划委托类型
     * loss_plan：止损计划
     **/
    public static final String BG_PLAN_TYPE_LOSS_PLAN = "loss_plan";


    /**
     * profit_plan：止盈计划
     **/
    public static final String BG_PLAN_TYPE_PROFIT_PLAN = "profit_plan";

    /**
     * fill_price 市场价格
     **/
    public static final String BG_TRIGGER_TYPE_FILL_PRICE = "fill_price";

    /**
     * mark_price 标记价格
     **/
    public static final String BG_TRIGGER_TYPE_MARK_PRICE = "mark_price";


}
