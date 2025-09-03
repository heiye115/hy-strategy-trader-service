# HY策略交易服务

## 项目简介

HY策略交易服务是一个基于Bitget交易所API的自动化交易系统，提供多种量化交易策略实现。该服务支持实时市场数据监控、自动下单、止盈止损管理等功能。

## 主要功能

- **交易策略**
    - 双均线交易策略 (V2)
    - 区间震荡交易策略 (V7)
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
bitget.api-Key=your_api_key
bitget.secret-key=your_secret_key
bitget.passphrase=your_passphrase
bitget.base-url=https://api.bitget.com
```

3. 构建项目

```bash
mvn clean install -Dmaven.test.skip=true
```

4. 运行服务

```bash
java -jar hy-strategy-trader-service.jar
```

- Linux下运行：
    - ```bash
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
private DualMovingAverageStrategyV2Service dualMovingAverageStrategyService;

@Autowired
private RangeTradingStrategyV7Service rangeTradingStrategyService;
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

基于两条不同周期的移动平均线产生交易信号：

- 当短期均线向上穿越长期均线时产生买入信号
- 当短期均线向下穿越长期均线时产生卖出信号

### 区间震荡策略

识别价格震荡区间进行高抛低吸：

- 自动识别近期价格波动范围
- 设置动态止盈止损
- 支持历史K线数据分析

## 任务调度

使用Spring的@Scheduled实现定时任务：

- 市场数据监控 (1秒间隔)
- 交易信号检测 (10秒间隔)
- 订单状态同步 (200毫秒间隔)
- 仓位管理 (2秒间隔)

## 日志与监控

- 完整的交易日志记录
- 异常处理机制
- 邮件通知功能

## 安全注意事项

- API密钥应妥善保管
- 建议使用独立的交易账户
- 设置适当的风控参数
- 定期监控交易日志

## 商务合作

承接量化交易策略开发与定制服务，欢迎联系：heiye115@gmail.com 微信: heiye5050

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
