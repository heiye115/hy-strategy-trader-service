# HY Strategy Trading Service

## Project Introduction
HY Strategy Trading Service is an automated trading system based on the Bitget Exchange API, providing implementations of various quantitative trading strategies. This service supports real-time market data monitoring, automatic order placement, and profit-taking and stop-loss management functions.

## Key Features
- **Trading Strategies**
  - Dual Moving Average Trading Strategy (V1/V2)
  - Range Trading Strategy (V5/V6/V7)
- **Account Management**
  - Leverage Settings
  - Margin Mode Management
  - Position Mode Configuration
- **Order Management**
  - Real-time Order Placement
  - Take Profit/Stop Loss Orders
  - Order Status Monitoring
- **Market Data**
  - Real-time Market Data Subscription
  - Historical K-line Data Retrieval
  - Market Signal Analysis

## Technical Architecture
This project is developed using the Spring Boot framework with a modular design:
- **Core Module**
  - Bitget API Client
  - Trading Strategy Implementation
  - Task Scheduling System
- **Functional Modules**
  - Market Data Analysis
  - Order Execution Engine
  - Risk Control System
- **Integrated Services**
  - Email Notifications
  - Thread Pool Management
  - Logging

## System Requirements
- Java 8+
- Spring Boot 2.x
- Bitget API Access
- Email Server Configuration (Optional)

## Installation & Deployment
1. Clone the repository
```bash
git clone https://gitee.com/heiye115/hy-strategy-trader-service.git
```
2. Configure Bitget API Credentials
```properties
# application.properties
bitget.api-Key=your_api_key
bitget.secret-key=your_secret_key
bitget.passphrase=your_passphrase
bitget.base-url=https://api.bitget.com
```
3. Build the project
```bash
mvn clean package
```
4. Run the service
```bash
java -jar hy-strategy-trader-service.jar
```

## Usage Instructions
1. **Start the Service**
```java
// Main startup class
com.hy.HyStratTraderServiceApplication
```

2. **Strategy Configuration**
- Configure trading parameters in the strategy service class:
  - Trading Pair (e.g., BTC/USDT)
  - Timeframe (e.g., 1h, 4h)
  - Position Size
  - Take Profit/Stop Loss Ratios
  - Leverage Settings

3. **Start the Strategy**
- Inject and start the desired strategy via Spring:
```java
@Autowired
private DualMovingAverageStrategyV2Service dualMovingAverageStrategyService;

@Autowired
private RangeTradingStrategyV7Service rangeTradingStrategyService;
```

## API Interface
The service encapsulates the complete API interface of Bitget Exchange, including:
- **Market Data**
  - Retrieve K-line Data
  - Real-time Market Data Subscription
  - Order Book Depth Query
- **Trading Functions**
  - Place/Cancel Orders
  - Take Profit/Stop Loss Order Management
  - Order Status Inquiry
- **Account Management**
  - Account Information Query
  - Leverage Settings
  - Margin Mode Management

## Strategy Implementation
### Dual Moving Average Strategy
Generates trading signals based on two moving averages with different periods:
- A buy signal is generated when the short-term moving average crosses above the long-term moving average
- A sell signal is generated when the short-term moving average crosses below the long-term moving average

### Range Trading Strategy
Performs trading within identified price ranges:
- Automatically identifies recent price fluctuations
- Sets dynamic take profit and stop loss levels
- Supports historical K-line data analysis

## Task Scheduling
Scheduled tasks are implemented using Spring's @Scheduled annotation:
- Market Data Monitoring (1-second interval)
- Trading Signal Detection (10-second interval)
- Order Status Synchronization (200-millisecond interval)
- Position Management (2-second interval)

## Logging & Monitoring
- Comprehensive trading log recording
- Exception handling mechanism
- Email notification functionality

## Security Notes
- API keys should be properly safeguarded
- It is recommended to use a dedicated trading account
- Set appropriate risk control parameters
- Regularly monitor trading logs

## Open Source License
This project is licensed under the Apache-2.0 License. Code contributions and suggestions are welcome.