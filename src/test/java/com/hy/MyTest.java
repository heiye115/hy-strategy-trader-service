package com.hy;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * 订单下单与平仓逻辑综合测试。
 */
@SpringBootTest
public class MyTest {

//    /**
//     * 统一客户端（测试网 + 启用调试日志）
//     */
//    HyperliquidClient client = HyperliquidClient.builder()
//            .testNetUrl()
//            //.addPrivateKey(privateKey)
//            .addApiWallet("0x7D71218e32634c93401D87A317acB1396636e339", "0xe84bb15e194bc4f7994d4d9e2e019e05b85424a02b89478c4c0e81135ccd52e3")
//            //.enableDebugLogs()
//            .build();
//
//    /**
//     * 市价下单
//     **/
//    @Test
//    public void testMarketOrder() {
//        OrderRequest req = OrderRequest.Open.market("ETH", true, "0.01");
//        Order order = client.getSingleExchange().order(req);
//        System.out.println(order);
//    }
//
//    /**
//     * 市价平仓
//     **/
//    @Test
//    public void testMarketCloseOrder() {
//        OrderRequest req = OrderRequest.Close.market("ETH", "0.01", Cloid.auto());
//        Order order = client.getSingleExchange().order(req);
//        System.out.println(order);
//    }
//
//    /**
//     * 全部市价平仓
//     **/
//    @Test
//    public void testMarketCloseAllOrder() {
//        Order order = client.getSingleExchange().closePositionMarket("ETH");
//        System.out.println(order);
//    }
//
//    /**
//     * 限价下单
//     **/
//    @Test
//    public void testLimitOrder() {
//        OrderRequest req = OrderRequest.Open.limit(Tif.GTC, "ETH", true, "0.01", "1800");
//        Order order = client.getSingleExchange().order(req);
//        System.out.println(order);
//    }
//
//    /**
//     * 限价平仓
//     **/
//    @Test
//    public void testLimitCloseOrder() {
//        OrderRequest req = OrderRequest.Close.limit(Tif.GTC, "ETH", "0.01", "4000", Cloid.auto());
//        Order order = client.getSingleExchange().order(req);
//        System.out.println(order);
//    }
//
//    /**
//     * 全部限价平仓
//     **/
//    @Test
//    public void testLimitCloseAllOrder() {
//        Order order = client.getSingleExchange().closePositionLimit(Tif.GTC, "ETH", "4000", Cloid.auto());
//        System.out.println(order);
//    }
//
//    @Test
//    public void testCancel() {
//        JsonNode node = client.getSingleExchange().cancel("ETH", 42988070692L);
//        System.out.println(node.toPrettyString());
//    }
//
//
//    @Test
//    public void testMarketOrderALL() {
//        OrderRequest req = OrderRequest.Open.market("ETH", true, "0.01");
//        Order order = client.getSingleExchange().order(req);
//        System.out.println(order);
//        Order closeOrder = client.getSingleExchange().closePositionMarket("ETH");
//        System.out.println(closeOrder);
//    }
//
//    @Test
//    public void testTriggerOrderALL() {
////        OrderRequest req = OrderRequest.Open.trigger("ETH", true, "0.01", "4000", "4000", true, TriggerOrderType.TpslType.TP);
////        Order order = client.getSingleExchange().order(req);
////        System.out.println(order);
//    }
//
//    @Test
//    public void testPositionTpsl() {
//        // 为已有的 ETH 多仓添加止盈止损
//        // closePosition(数量, 是否多仓)
//        // 假设你持有 0.01 ETH 多仓
//        OrderGroup orderGroup = OrderRequest.entryWithTpSl()
//                .perp("ETH")
//                .closePosition("0.01", true)// 0.01 ETH 多仓（true表示多仓）
//                .stopLoss("2700")
//                .takeProfit("3100")
//                .buildPositionTpsl();
//        JsonNode jsonNode = client.getSingleExchange().bulkOrders(orderGroup);
//        System.out.println(jsonNode);
//    }
//
//    @Test
//    public void testNormalTpsl() {
//        OrderGroup orderGroup = OrderRequest.entryWithTpSl()
//                .perp("ETH")
//                .buy("0.01")
//                .cloid(Cloid.auto())
//                .stopLoss("2700")
//                //.takeProfit("3100")
//                .buildNormalTpsl();
//        JsonNode jsonNode = client.getSingleExchange().bulkOrders(orderGroup);
//        System.out.println(jsonNode);
//    }
//
//    @Test
//    public void testPositionTpslAutoInfer() {
//        // 自动推断仓位方向和数量（无需调用 closePosition）
//        // Exchange 会自动查询账户 ETH 持仓并推断方向和数量
//        OrderGroup orderGroup = OrderRequest.entryWithTpSl()
//                .perp("ETH")
//                // 不调用 closePosition()，由 SDK 自动推断
//                .stopLoss("2700")
//                //.takeProfit(3100.0)
//                .buildPositionTpsl();
//        JsonNode jsonNode = client.getSingleExchange().bulkOrders(orderGroup);
//        System.out.println(jsonNode);
//    }

}

 
