# HY Strategy Trading Service

## Project Introduction

HY Strategy Trading Service is an automated trading system based on the Bitget Exchange API, providing various quantitative trading strategy implementations. The service supports real-time market data monitoring, automated order placement, take-profit and stop-loss management, and more.

## Main Features

- **Trading Strategies**
    - Dual Moving Average Strategy: A trend-following and breakout strategy based on a multi-MA/EMA system. High risk-reward ratio, supports dynamic leverage.
    - Range Trading Strategy: Suitable for wide-range oscillation or swing markets. Balanced win rate and risk-reward ratio, supports both long and short directions.
    - Short-term Strategy: Suitable for short-term market fluctuations. High risk, high return, supports both long and short directions.
    - Martingale Strategy: Suitable for oscillating markets. Stable, high win rate, supports both long and short directions.

- **Account Management**
    - Leverage Settings
    - Margin Mode Management
    - Position Mode Settings

- **Order Management**
    - Real-time Order Placement
    - Take Profit and Stop Loss (TP/SL) Orders
    - Order Status Monitoring

- **Market Data**
    - Real-time Market Data Subscription
    - Historical K-line Data Retrieval
    - Market Signal Analysis

## Technical Architecture

This project is developed based on the Spring Boot framework with a modular design:

- **Core Modules**
    - Bitget API Client
    - Trading Strategy Implementations
    - Task Scheduling System
- **Functional Modules**
    - Market Data Analysis
    - Order Execution Engine
    - Risk Control System
- **Integrated Services**
    - Email Notification
    - Thread Pool Management
    - Logging

## System Requirements

- Java 21+
- Spring Boot 3.5.x
- Bitget API Access Permissions
- Mail Server Configuration (Optional)

## Installation and Deployment

1. Clone the Repository

```bash
git clone https://gitee.com/heiye115/hy-strategy-trader-service.git
```

2. Configure Bitget API Credentials

```properties
# application.properties
# Account 1: Dedicated API Key for Martingale Strategy
bitget.accounts[0].name=MARTINGALE
bitget.accounts[0].api-key=Your API Key
bitget.accounts[0].secret-key=Your Secret Key
bitget.accounts[0].passphrase=Your Passphrase
# Account 2: Dedicated API Key for Dual Moving Average Strategy
bitget.accounts[1].name=DMA
bitget.accounts[1].api-key=Your API Key
bitget.accounts[1].secret-key=Your Secret Key
bitget.accounts[1].passphrase=Your Passphrase
```

3. Build the Project

```bash
mvn clean package -Dmaven.test.skip=true
```

4. Run the Service

```bash
java -jar hy-strategy-trader-service.jar
```

- Running on Linux:
    ```bash
    setsid java -Djasypt.encryptor.password=YourPassword -jar hy-strategy-trader-service.jar > app.log 2>&1 &
    ```

## Usage Instructions

1. **Start the Service**

```java
// Main application class
com.hy.HyStratTraderServiceApplication
```

2. **Strategy Configuration**

- Configure trading parameters in the strategy service class:
    - Trading Pairs (BTC/USDT, etc.)
    - Timeframes (1h, 4h, etc.)
    - Open Amount
    - Take Profit and Stop Loss Ratios
    - Leverage Settings

3. **Strategy Activation**

- Activate specific strategies via Spring injection:

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

## API Interface

The service encapsulates the complete Bitget Exchange API interface, including:

- **Market Data**
    - Get K-line Data
    - Subscribe to Real-time Market Data
    - Depth Order Book Query
- **Trading Features**
    - Place/Cancel Orders
    - TP/SL Order Management
    - Order Status Query
- **Account Management**
    - Account Information Query
    - Leverage Settings
    - Margin Mode Management

## Strategy Implementation

### Dual Moving Average Strategy

A composite trend-following and breakout strategy based on a combination of multiple Moving Averages (MA/EMA):

- **Multi-dimensional Indicator Verification**: Simultaneously uses 21, 55, and 144-period Simple Moving Averages (MA) and Exponential Moving Averages (EMA) for trend confirmation.
- **Strict Trend Determination**:
    - **Long Signal**: The short-term average (21) is above the medium-term (55) and long-term (144) averages, and the medium-term average is above the long-term average, with both MA and EMA systems showing a consistent bullish alignment.
    - **Short Signal**: The short-term average (21) is below the medium-term (55) and long-term (144) averages, and the medium-term average is below the long-term average, with both MA and EMA systems showing a consistent bearish alignment.
- **Breakout Trading Logic**: Triggered when the latest price effectively breaks above or below all MA and EMA indicator lines.
- **Dynamic Risk Control**:
    - **Cooldown Period**: Prevents frequent opening of positions within a short time via `cooldownMinutes` configuration, avoiding erosion during oscillating markets.
    - **Dynamic Leverage**: Automatically adjusts leverage (e.g., 5x/10x/20x) based on the deviation (volatility) of the price from the averages, reducing risks in extreme market conditions.
- **Bidirectional Support**: Supports both trend-following and breakout modes, with automated TP/SL management.

### Range Trading Strategy

Identifies price oscillation ranges for "sell high, buy low" operations:

- Automatically identifies recent price volatility ranges
- Sets dynamic TP/SL
- Supports historical K-line data analysis

### Short-term Strategy

Utilizes short-term price fluctuations for rapid trading:

- Rapidly captures market volatility
- High-frequency order placement
- Suitable for intraday trading

### Martingale Strategy

Based on the Martingale principle for capital management:

- Small initial position size
- Increase position size proportionally after each loss
- Suitable for oscillating markets

## Task Scheduling

Uses Spring's `@Scheduled` for periodic tasks:

- Market Data Monitoring (1-second interval)
- Trading Signal Detection (200ms - 1s interval)
- Order Execution Monitoring (200ms interval)
- Position Management (1s - 2s interval)

## Logging and Monitoring

- Comprehensive trading logs
- Exception handling mechanism
- Email notification feature

## Security Precautions

- Highly recommended to use sub-accounts for API operations
- API keys should be kept secure
- Recommended to use independent trading accounts
- Set appropriate risk control parameters
- Regularly monitor trading logs

## Business Cooperation

Providing quantitative trading strategy development and customization services. Contact: heiye115@gmail.com WeChat: [heiye5050].

- **Notes**
    - Use my invitation code: xq5v
    - Bitget Spot: 50% | Futures: 50% | On-chain: 40% fee reduction
    - Spot: 0.05% 👈 ⏬ 0.1%
    - Futures Limit: 0.01% 👈 ⏬ 0.02%
    - Futures Market: 0.03% 👈 ⏬ 0.06%
    - On-chain: 0.3% 👈 ⏬ 0.5%
    - https://www.bitget.cloud/zh-CN/register?channelCode=327r&vipCode=xq5v

## Disclaimer

1. **Risk Warning**: Quantitative trading involves high risk. Market volatility may lead to partial or total loss of funds. This project is for technical research and educational reference only and does not constitute investment advice.
2. **Live Trading Caution**: Users should fully understand the strategy logic and are strongly advised to conduct thorough testing on demo accounts before live trading. The authors and contributors are not responsible for any trading losses caused by code defects, system failures, network delays, or API errors.
3. **Personal Responsibility**: Users bear full responsibility for their trading behavior and configuration parameters. By using this project, you acknowledge and agree to assume all associated risks.
4. **Legal Compliance**: Please ensure that your trading activities comply with local laws and regulations.

---
**Trading involves risk. Invest with caution.**
