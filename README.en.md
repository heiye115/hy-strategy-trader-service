# HY Strategy Trading Service

## Project Overview

HY Strategy Trading Service is an automated trading system based on the Bitget Exchange API, providing implementations
of various quantitative trading strategies. The service supports real-time market data monitoring, automatic order
placement, and take-profit/stop-loss management.

## Main Features

- **Trading Strategies**
    - Dual Moving Average Strategy (V2): Suitable for trending markets, high risk-reward ratio, supports both long and
      short positions
    - Range Trading Strategy (V7): Suitable for wide-range or swing markets, balanced win rate and risk-reward ratio,
      supports both long and short positions
    - Short-term Strategy (V1): Suitable for short-term markets, high risk and high return, supports both long and short
      positions
    - Martingale Strategy: Suitable for ranging markets, stable and high win rate, supports both long and short
      positions
- **Account Management**
    - Leverage settings
    - Margin mode management
    - Position mode settings
- **Order Management**
    - Real-time order placement
    - Take-profit/stop-loss orders
    - Order status monitoring
- **Market Data**
    - Real-time market data subscription
    - Historical K-line data retrieval
    - Market signal analysis

## Technical Architecture

This project is developed based on the Spring Boot framework with modular design:

- **Core Modules**
    - Bitget API client
    - Trading strategy implementation
    - Task scheduling system
- **Functional Modules**
    - Market data analysis
    - Order execution engine
    - Risk control system
- **Integration Services**
    - Email notification
    - Thread pool management
    - Logging

## System Requirements

- Java 21+
- Spring Boot 3.5.x
- Bitget API access
- Email server configuration (optional)

## Installation & Deployment

1. Clone the repository

```bash
git clone https://gitee.com/heiye115/hy-strategy-trader-service.git
```

2. Configure Bitget API credentials

```properties
# application.properties
# Account 1: Martingale strategy dedicated API Key
bitget.accounts[0].api-key=Your API Key
bitget.accounts[0].secret-key=Your Secret Key
bitget.accounts[0].passphrase=Your Passphrase
# Account 2: Dual Moving Average strategy dedicated API Key
bitget.accounts[1].api-key=Your API Key
bitget.accounts[1].secret-key=Your Secret Key
bitget.accounts[1].passphrase=Your Passphrase

```

3. Build the project

```bash
mvn clean package -Dmaven.test.skip=true
```

4. Run the service

```bash
java -jar hy-strategy-trader-service.jar

For Linux:
setsid java -Djasypt.encryptor.password=your_password -jar hy-strategy-trader-service.jar > app.log 2>&1 &
```

## Usage Instructions

1. **Start the Service**

```java
// Main class to start
com.hy.HyStratTraderServiceApplication
```

2. **Strategy Configuration**

- Configure trading parameters in the strategy service class:
    - Trading pair (BTC/USDT, etc.)
    - Time period (1h, 4h, etc.)
    - Position size
    - Take-profit and stop-loss ratios
    - Leverage settings

3. **Start Strategy**

- Use Spring injection to start the specified strategy:

```java

@Autowired
private DualMovingAverageStrategyV2Service dualMovingAverageStrategyService;

@Autowired
private RangeTradingStrategyV7Service rangeTradingStrategyService;
```

## API Interface

The service encapsulates the complete Bitget exchange API, including:

- **Market Data**
    - Get K-line data
    - Real-time market subscription
    - Depth order book query
- **Trading Functions**
    - Place/cancel orders
    - Take-profit and stop-loss order management
    - Order status query
- **Account Management**
    - Account information query
    - Leverage settings
    - Margin mode management

## Strategy Implementation

### Dual Moving Average Strategy

Generates trading signals based on two moving averages of different periods:

- Buy signal when the short-term MA crosses above the long-term MA
- Sell signal when the short-term MA crosses below the long-term MA

### Range Oscillation Strategy

Identifies price oscillation ranges for buying low and selling high:

- Automatically identifies recent price fluctuation ranges
- Sets dynamic take-profit and stop-loss
- Supports historical K-line data analysis

### Short-term Strategy

Quick trading based on short-term price fluctuations:

- Quickly captures market volatility
- High-frequency order placement
- Suitable for intraday trading

### Martingale Strategy

Capital management based on the Martingale principle:

- Small initial position
- Increase position size proportionally after each loss
- Suitable for ranging markets

## Task Scheduling

Uses Spring's @Scheduled for scheduled tasks:

- Market data monitoring (1 second interval)
- Trading signal detection (10 seconds interval)
- Order status synchronization (200 milliseconds interval)
- Position management (2 seconds interval)

## Logging & Monitoring

- Complete trading log records
- Exception handling mechanism
- Email notification function

## Security Notes

- Strongly recommend using sub-accounts for API operations
- API keys should be kept safe
- It is recommended to use a separate trading account
- Set appropriate risk control parameters
- Regularly monitor trading logs

## Business Cooperation

We undertake quantitative trading strategy development and customization services. Contact: heiye115@gmail.com

- **Note**
    - Use my invitation code xq5v
    - Bitget Spot 50%｜Contract 50%｜On-chain trading 40% fee discount
    - Spot: 0.05% 👈⏬0.1%
    - Contract limit: 0.01% 👈⏬0.02%
    - Contract market: 0.03% 👈⏬0.06%
    - On-chain trading: 0.3% 👈⏬0.5%
    - https://www.bitget.cloud/zh-CN/register?channelCode=327r&vipCode=xq5v

## Disclaimer

This project is for learning and research purposes only. Users bear their own trading risks. The author is not
responsible for any losses resulting from the use of this project.