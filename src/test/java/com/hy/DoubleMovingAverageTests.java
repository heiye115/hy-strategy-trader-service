package com.hy;

import com.hy.modules.contract.service.DoubleMovingAverageStrategyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class DoubleMovingAverageTests {

    @Autowired
    DoubleMovingAverageStrategyService doubleMovingAverageStrategyService;

    @Test
    public void test1() {
        doubleMovingAverageStrategyService.managePositions();
    }
}
