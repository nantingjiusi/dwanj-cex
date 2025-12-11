package com.remus.dwanjcex;

import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import com.remus.dwanjcex.wallet.services.OrderService;
import com.remus.dwanjcex.wallet.services.UserService;
import com.remus.dwanjcex.wallet.services.WalletService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public class MarketMakerTest {

    @Autowired
    private UserService userService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private OrderService orderService;



    private static final int USER_COUNT = 100;
    private static final int ORDERS_PER_USER = 100; // 每个用户下的单数
    private static final String SYMBOL = "BTCUSDT";
    private static final BigDecimal INITIAL_USDT = new BigDecimal("1000000");
    private static final BigDecimal INITIAL_BTC = new BigDecimal("10000");
    private static final BigDecimal BASE_PRICE = new BigDecimal("50000");

    @Test
    public void runMarketMaker() throws InterruptedException {
        log.info("开始初始化做市机器人测试...");

        List<Long> userIds = new ArrayList<>();
        Random random = new Random();

        // 1. 注册用户并充值
        for (int i = 0; i < USER_COUNT; i++) {
            String username = "bot_" + System.currentTimeMillis() + "_" + i;
            try {
                Long userId = userService.genUser(username, "123");

                // 充值
                walletService.deposit(userId, "USDT", INITIAL_USDT, "init");
                walletService.deposit(userId, "BTC", INITIAL_BTC, "init");
                
                userIds.add(userId);
                log.info("用户 {} 初始化完成 (ID: {})", username, userId);
            } catch (Exception e) {
                log.error("用户初始化失败: {}", username, e);
            }
        }

        log.info("用户初始化完成，共 {} 个用户。开始并发下单...", userIds.size());

        ExecutorService executor = Executors.newFixedThreadPool(USER_COUNT);
        CountDownLatch latch = new CountDownLatch(USER_COUNT);
        AtomicInteger totalOrders = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // 2. 并发下单
        for (Long userId : userIds) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < ORDERS_PER_USER; j++) {
                        try {
                            // 随机生成订单参数
                            OrderTypes.Side side = random.nextBoolean() ? OrderTypes.Side.BUY : OrderTypes.Side.SELL;
                            OrderTypes.OrderType type = random.nextInt(10) < 8 ? OrderTypes.OrderType.LIMIT : OrderTypes.OrderType.MARKET; // 80%限价单
                            
                            BigDecimal price = null;
                            BigDecimal amount = null;
                            BigDecimal quoteAmount = null;

                            // 价格在基准价格上下浮动 5%
                            double priceFluctuation = (random.nextDouble() - 0.5) * 0.1;
                            BigDecimal currentPrice = BASE_PRICE.multiply(BigDecimal.valueOf(1 + priceFluctuation)).setScale(2, RoundingMode.HALF_UP);

                            if (type == OrderTypes.OrderType.LIMIT) {
                                price = currentPrice;
                                amount = BigDecimal.valueOf(random.nextDouble() * 0.5 + 0.01).setScale(6, RoundingMode.HALF_UP); // 0.01 ~ 0.51 BTC
                            } else {
                                if (side == OrderTypes.Side.BUY) {
                                    quoteAmount = currentPrice.multiply(BigDecimal.valueOf(random.nextDouble() * 0.5 + 0.01)).setScale(2, RoundingMode.HALF_UP);
                                } else {
                                    amount = BigDecimal.valueOf(random.nextDouble() * 0.5 + 0.01).setScale(6, RoundingMode.HALF_UP);
                                }
                            }

                            OrderDto orderDto = OrderDto.builder()
                                    .userId(userId)
                                    .symbol(SYMBOL)
                                    .type(type)
                                    .side(side)
                                    .price(price)
                                    .amount(amount)
                                    .quoteAmount(quoteAmount)
                                    .build();

                            orderService.placeOrder(orderDto);
                            totalOrders.incrementAndGet();
                            
                            // 模拟思考时间，避免瞬间压垮
                            Thread.sleep(random.nextInt(50)); 

                        } catch (Exception e) {
                            log.warn("下单失败: {}", e.getMessage());
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("测试结束！");
        log.info("总耗时: {} ms", duration);
        log.info("总订单数: {}", totalOrders.get());
        log.info("平均TPS: {}", (totalOrders.get() * 1000.0 / duration));
    }
}
