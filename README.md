# HY策略交易服务

## 项目简介

HY策略交易服务是一个基于Bitget交易所API的自动化交易系统，提供多种量化交易策略实现。该服务支持实时市场数据监控、自动下单、止盈止损管理等功能。

## 主要功能

- **交易策略**
    - 双均线策略：基于MA/EMA多重均线系统的趋势追踪与突破策略。高盈亏比，支持动态杠杆。
    - 区间震荡策略：适合宽幅震荡或波段行情。胜率和盈亏比均衡，支持多空双向。
    - 短线策略：适合短线行情。高风险、高收益，支持多空双向。
    - 马丁格尔策略：适合震荡行情。稳健、高胜率，支持多空双向。

- **账户管理**
    - 杠杆设置
    - 保证金模式管理
    - 仓位模式设置

- **订单管理**
    - 实时下单
    - 止盈止损订单
    - 订单状态监控

- **市场数据**
    - 实时行情订阅
    - 历史K线数据获取
    - 市场信号分析

## 技术架构

本项目基于Spring Boot框架开发，采用模块化设计：

- **核心模块**
    - Bitget API客户端
    - 交易策略实现
    - 任务调度系统
- **功能模块**
    - 市场数据分析
    - 订单执行引擎
    - 风险控制系统
- **集成服务**
    - 邮件通知
    - 线程池管理
    - 日志记录

## 系统要求

- Java 21+
- Spring Boot 3.5.x
- Bitget API访问权限
- 邮件服务器配置（可选）

## 安装部署

1. 克隆仓库

```bash
git clone https://gitee.com/heiye115/hy-strategy-trader-service.git
```

2. 配置Bitget API凭证

```properties
# application.properties
# 第1个账号 马丁格尔策略专用API Key
bitget.accounts[0].name=MARTINGALE
bitget.accounts[0].api-key=您的API Key
bitget.accounts[0].secret-key=您的Secret Key
bitget.accounts[0].passphrase=您的Passphrase
# 第2个账号 双均线策略专用API Key
bitget.accounts[1].name=DMA
bitget.accounts[1].api-key=您的API Key
bitget.accounts[1].secret-key=您的Secret Key
bitget.accounts[1].passphrase=您的Passphrase
```

3. 构建项目

```bash
mvn clean package -Dmaven.test.skip=true
```

4. 运行服务

```bash
java -jar hy-strategy-trader-service.jar
```

- Linux下运行：
    ```bash
    setsid java -Djasypt.encryptor.password=您的密码 -jar hy-strategy-trader-service.jar > app.log 2>&1 &
    ```

## 使用说明

1. **启动服务**

```java
// 启动主类
com.hy.HyStratTraderServiceApplication
```

2. **策略配置**

- 在策略服务类中配置交易参数：
    - 交易对 (BTC/USDT等)
    - 时间周期 (1h, 4h等)
    - 开仓金额
    - 止盈止损比例
    - 杠杆设置

3. **策略启动**

- 通过Spring注入启动指定策略：

```java

@Autowired
private DoubleMovingAverageStrategyService doubleMovingAverageStrategyService;

@Autowired
private RangeTradingStrategyService rangeTradingStrategyService;

@Autowired
private MartingaleStrategyService martingaleStrategyService;

@Autowired
private ShortTermTradingStrategyService shortTermTradingStrategyService;
```

## API接口

服务封装了Bitget交易所的完整API接口，包括：

- **市场数据**
    - 获取K线数据
    - 实时行情订阅
    - 深度订单簿查询
- **交易功能**
    - 下单/撤单
    - 止盈止损订单管理
    - 订单状态查询
- **账户管理**
    - 账户信息查询
    - 杠杆设置
    - 保证金模式管理

## 策略实现

### 双均线策略

基于多重移动平均线（MA/EMA）组合的复合趋势追踪与突破策略：

- **多维指标验证**：同时使用 21、55、144 周期的简单移动平均线（MA）和指数移动平均线（EMA）进行趋势确认。
- **严格趋势判定**：
    - **做多信号**：短期均线（21）位于中期（55）和长期（144）均线之上，且中期均线位于长期均线之上，同时 MA 和 EMA 系统需形成一致的多头排列。
    - **做空信号**：短期均线（21）位于中期（55）和长期（144）均线之下，且中期均线位于长期均线之下，同时 MA 和 EMA 系统需形成一致的空头排列。
- **突破交易逻辑**：当最新价格有效突破（高于或低于）所有 MA 和 EMA 指标线时，触发突破入场信号。
- **动态风险控制**：
    - **冷却期机制**：通过配置 `cooldownMinutes` 避免在短时间内频繁开仓，防止震荡行情的反复磨损。
    - **动态杠杆**：根据价格与均线的偏离度（波动率）动态调整杠杆倍数（如 5x/10x/20x），降低极端行情风险。
- **多空双向支持**：支持趋势跟随和突破两种模式，具备自动止盈止损管理。

### 区间震荡策略

识别价格震荡区间进行高抛低吸：

- 自动识别近期价格波动范围
- 设置动态止盈止损
- 支持历史K线数据分析

### 短线策略

利用短期价格波动进行快速交易：

- 快速捕捉市场波动
- 高频率下单
- 适合日内交易

### 马丁格尔策略

基于马丁格尔原理进行资金管理：

- 初始仓位较小
- 每次亏损后按比例加仓位
- 适合震荡行情

## 任务调度

使用Spring的@Scheduled实现定时任务：

- 市场数据监控 (1秒间隔)
- 交易信号检测 (200毫秒 - 1秒间隔)
- 订单执行监控 (200毫秒间隔)
- 仓位管理 (1秒 - 2秒间隔)

## 日志与监控

- 完整的交易日志记录
- 异常处理机制
- 邮件通知功能

## 安全注意事项

- 强烈建议使用子账户进行API操作
- API密钥应妥善保管
- 建议使用独立的交易账户
- 设置适当的风控参数
- 定期监控交易日志

## 商务合作

承接量化交易策略开发与定制服务，欢迎联系：heiye115@gmail.com 微信:【heiye5050】。

- **备注**
    - 使用我的邀请码xq5v
    - Bitget 现货50%｜合约50%｜链上交易40%手续费减免
    - 现货：0.05%👈⏬0.1%
    - 合约限价：0.01%👈⏬0.02%
    - 合约市价：0.03%👈⏬0.06%
    - 链上交易：0.3%👈⏬0.5%
    - https://www.bitget.cloud/zh-CN/register?channelCode=327r&vipCode=xq5v

## 免责申明

本项目仅供学习和研究使用，用户需自行承担交易风险。作者不对因使用本项目而产生的任何损失负责。