package com.remus.dwanjcex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remus.dwanjcex.common.OrderTypes;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MarketMakerBot {

    private static final String API_BASE_URL = "http://localhost:8080";
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int USER_COUNT = 20;
    private static final String SYMBOL = "BTCUSDT";
    private static final BigDecimal INITIAL_USDT = new BigDecimal("1000000");
    private static final BigDecimal INITIAL_BTC = new BigDecimal("100");
    private static final BigDecimal BASE_PRICE = new BigDecimal("50000");

    public static void main(String[] args) throws InterruptedException {
        System.out.println("--- Market Maker Bot Starting ---");
        List<BotUser> bots = new ArrayList<>();

        // 1. 初始化用户
        for (int i = 0; i < USER_COUNT; i++) {
            String username = "bot_client_" + System.currentTimeMillis() + "_" + i;
            String password = "123";
            try {
                // 注册
                restTemplate.postForEntity(API_BASE_URL + "/auth/register", new UserCredentials(username, password), String.class);
                
                // 登录获取Token
                ResponseEntity<String> loginResponse = restTemplate.postForEntity(API_BASE_URL + "/auth/login", new UserCredentials(username, password), String.class);
                
                // 【关键调试】打印响应详情
                if (loginResponse.getStatusCode() != HttpStatus.OK || loginResponse.getBody() == null) {
                    System.err.println("登录失败! Status: " + loginResponse.getStatusCode() + ", Body: " + loginResponse.getBody());
                    continue;
                }

                JsonNode loginNode = objectMapper.readTree(loginResponse.getBody());
                String token = loginNode.get("data").get("token").asText();

                // 充值
                deposit(token, "USDT", INITIAL_USDT);
                deposit(token, "BTC", INITIAL_BTC);

                bots.add(new BotUser(username, token));
                System.out.println("Initialized bot: " + username);
            } catch (Exception e) {
                System.err.println("Failed to initialize bot " + username + ": " + e.getMessage());
                // e.printStackTrace(); // 打印堆栈以便排查
            }
        }

        System.out.println("\n--- All bots initialized. Starting concurrent trading... ---");
        System.out.println("--- Trading indefinitely. Stop the process manually in your IDE to exit. ---");
        ExecutorService executor = Executors.newFixedThreadPool(USER_COUNT);
        Random random = new Random();

        // 2. 并发下单
        for (BotUser bot : bots) {
            executor.submit(() -> {
                while (true) {
                    try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.setBearerAuth(bot.token);

                        OrderDto orderDto = createRandomOrder();
                        HttpEntity<OrderDto> request = new HttpEntity<>(orderDto, headers);

                        restTemplate.postForEntity(API_BASE_URL + "/api/order/place", request, String.class);
                        
                        Thread.sleep(random.nextInt(500) + 500); // 500-1000ms 间隔
                    } catch (Exception e) {
                        System.err.println("An error occurred while placing order for " + bot.username + ": " + e.getMessage());
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            });
        }
    }

    private static void deposit(String token, String asset, BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        HttpEntity<DepositRequest> request = new HttpEntity<>(new DepositRequest(asset, amount), headers);
        restTemplate.postForEntity(API_BASE_URL + "/api/wallet/deposit", request, String.class);
    }

    private static OrderDto createRandomOrder() {
        Random random = new Random();
        OrderTypes.Side side = random.nextBoolean() ? OrderTypes.Side.BUY : OrderTypes.Side.SELL;
        OrderTypes.OrderType type = random.nextInt(10) < 8 ? OrderTypes.OrderType.LIMIT : OrderTypes.OrderType.MARKET;
        
        BigDecimal price = null;
        BigDecimal amount = null;
        BigDecimal quoteAmount = null;

        double priceFluctuation = (random.nextDouble() - 0.5) * 0.05;
        BigDecimal currentPrice = BASE_PRICE.multiply(BigDecimal.valueOf(1 + priceFluctuation)).setScale(2, RoundingMode.HALF_UP);

        if (type == OrderTypes.OrderType.LIMIT) {
            price = currentPrice;
            amount = BigDecimal.valueOf(random.nextDouble() * 0.1 + 0.001).setScale(6, RoundingMode.HALF_UP);
        } else {
            if (side == OrderTypes.Side.BUY) {
                quoteAmount = currentPrice.multiply(BigDecimal.valueOf(random.nextDouble() * 0.1 + 0.001)).setScale(2, RoundingMode.HALF_UP);
            } else {
                amount = BigDecimal.valueOf(random.nextDouble() * 0.1 + 0.001).setScale(6, RoundingMode.HALF_UP);
            }
        }

        return new OrderDto(SYMBOL, type, side, price, amount, quoteAmount);
    }

    // Helper classes
    private static class UserCredentials {
        public String username;
        public String password;
        public UserCredentials(String username, String password) { this.username = username; this.password = password; }
    }

    private static class BotUser {
        public String username;
        public String token;
        public BotUser(String username, String token) { this.username = username; this.token = token; }
    }

    private static class DepositRequest {
        public String asset;
        public BigDecimal amount;
        public DepositRequest(String asset, BigDecimal amount) { this.asset = asset; this.amount = amount; }
    }

    private static class OrderDto {
        public String symbol;
        public OrderTypes.OrderType type;
        public OrderTypes.Side side;
        public BigDecimal price;
        public BigDecimal amount;
        public BigDecimal quoteAmount;
        public OrderDto(String symbol, OrderTypes.OrderType type, OrderTypes.Side side, BigDecimal price, BigDecimal amount, BigDecimal quoteAmount) {
            this.symbol = symbol; this.type = type; this.side = side; this.price = price; this.amount = amount; this.quoteAmount = quoteAmount;
        }
    }
}
