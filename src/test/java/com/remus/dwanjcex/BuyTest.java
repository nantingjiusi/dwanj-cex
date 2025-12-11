package com.remus.dwanjcex;

import com.remus.dwanjcex.common.CoinConstant;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.Trade;
import com.remus.dwanjcex.wallet.entity.dto.OrderBookLevel;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import com.remus.dwanjcex.wallet.services.OrderService;
import com.remus.dwanjcex.wallet.services.TradeService;
import com.remus.dwanjcex.wallet.services.UserService;
import com.remus.dwanjcex.wallet.services.WalletService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest
public class BuyTest {

    private static final String SYMBOL_BTC_USDT = "BTCUSDT";
    private static final String SYMBOL_ETH_USDT = "ETHUSDT";

    @Resource
    private UserService userService;

    @Resource
    private WalletService walletService;

    @Resource
    private OrderService orderService;

    @Resource
    private TradeService tradeService;

    @Test
    public void t_createUser(){
        Long buyerId = userService.genUser("admin"+ UUID.randomUUID().toString().substring(0,5), "123");
        walletService.deposit(buyerId, CoinConstant.USDT, new BigDecimal("1000000"),"Deposit");
        walletService.deposit(buyerId,CoinConstant.BTC,new BigDecimal("50"),"Deposit");

        Long buyerId2 = userService.genUser("admin"+ UUID.randomUUID().toString().substring(0,5), "123");
        walletService.deposit(buyerId2, CoinConstant.USDT, new BigDecimal("1000000"),"Deposit");
        walletService.deposit(buyerId2,CoinConstant.BTC,new BigDecimal("50"),"Deposit");
    }

    @Test
    public void t_cancelOrder(){
        Long buyerId = userService.genUser("admin"+ UUID.randomUUID().toString().substring(0,5), "123");
        System.out.println("Buyer ID: " + buyerId);
        System.out.println("买方充值100W USD");
        walletService.deposit(buyerId, CoinConstant.USDT, new BigDecimal("1000000"),"Deposit");
        OrderDto buyOrder = new OrderDto();
        buyOrder.setUserId(buyerId);
        buyOrder.setSide(OrderTypes.Side.BUY);
        buyOrder.setSymbol(SYMBOL_ETH_USDT);
        buyOrder.setPrice(new BigDecimal("50000"));
        buyOrder.setAmount(new BigDecimal("2"));
        OrderEntity orderEntity = orderService.placeOrder(buyOrder);
        System.out.println("取消前打印");
        printOrderBook(SYMBOL_ETH_USDT);
        System.out.println("准备取消");
        orderService.cancelOrder(buyerId,orderEntity.getId());
        System.out.println("取消后打印");
        printOrderBook(SYMBOL_ETH_USDT);
    }

    @Test
    public void t_buyBtc()  {
        // 1️⃣ 注册买方和卖方
        Long buyerId = userService.genUser("admin"+ UUID.randomUUID().toString().substring(0,5), "123");
        Long sellerId = userService.genUser("admin"+ UUID.randomUUID().toString().substring(0,5), "123");

        System.out.println("Buyer ID: " + buyerId);
        System.out.println("Seller ID: " + sellerId);

        //买方充值100W USD
        System.out.println("买方充值100W USD");
        walletService.deposit(buyerId, CoinConstant.USDT, new BigDecimal("1000000"),"Deposit");
        System.out.println("Buyer BTC balance: " + walletService.getBalance(buyerId, CoinConstant.BTC));
        System.out.println("Buyer USDT balance: " + walletService.getBalance(buyerId, CoinConstant.USDT));
        System.out.println("Seller USD balance: " + walletService.getBalance(sellerId, CoinConstant.USDT));
        System.out.println("Seller BTC balance: " + walletService.getBalance(sellerId, CoinConstant.BTC));



        //卖方充值50BTC
        walletService.deposit(sellerId,CoinConstant.BTC,new BigDecimal("50"),"Deposit");
        System.out.println("Buyer BTC balance: " + walletService.getBalance(buyerId, CoinConstant.BTC));
        System.out.println("Buyer USDT balance: " + walletService.getBalance(buyerId, CoinConstant.USDT));
        System.out.println("Seller USD balance: " + walletService.getBalance(sellerId, CoinConstant.USDT));
        System.out.println("Seller BTC balance: " + walletService.getBalance(sellerId, CoinConstant.BTC));

        // 2️⃣ 买方下单买 2 BTC，价格 50000 USD

        OrderDto buyOrder = new OrderDto();
        buyOrder.setUserId(buyerId);
        buyOrder.setSide(OrderTypes.Side.BUY);
        buyOrder.setSymbol(SYMBOL_BTC_USDT);
        buyOrder.setPrice(new BigDecimal("50000"));
        buyOrder.setAmount(new BigDecimal("2"));
        orderService.placeOrder(buyOrder);




        System.out.println("Buy order placed: " + buyOrder);

        // 3️⃣ 查看当前订单簿

        printOrderBook(SYMBOL_BTC_USDT);

        // 4️⃣ 卖方下单卖 1 BTC，价格 50000 USD
        OrderDto sellOrder = new OrderDto();
        sellOrder.setUserId(sellerId);
        sellOrder.setSide(OrderTypes.Side.SELL);
        sellOrder.setSymbol(SYMBOL_BTC_USDT);
        sellOrder.setPrice(new BigDecimal("50000"));
        sellOrder.setAmount(new BigDecimal("1"));
        orderService.placeOrder(sellOrder);

        System.out.println("Sell order placed: " + sellOrder);
        printOrderBook(SYMBOL_BTC_USDT);
//        卖方第二次下单卖 1 BTC，价格 50000 USD
        OrderDto sellOrder2 = new OrderDto();
        sellOrder2.setUserId(sellerId);
        sellOrder2.setSide(OrderTypes.Side.SELL);
        sellOrder2.setSymbol(SYMBOL_BTC_USDT);
        sellOrder2.setPrice(new BigDecimal("50000"));
        sellOrder2.setAmount(new BigDecimal("3"));
        orderService.placeOrder(sellOrder2);
        printOrderBook(SYMBOL_BTC_USDT);

        Long buyerId2 = userService.genUser("admin"+ UUID.randomUUID().toString().substring(0,5), "123");
        System.out.println("买方充值100W USD");
        walletService.deposit(buyerId2, CoinConstant.USDT, new BigDecimal("1000000"),"Deposit");
        OrderDto buyOrder2 = new OrderDto();
        buyOrder2.setUserId(buyerId2);
        buyOrder2.setSide(OrderTypes.Side.BUY);
        buyOrder2.setSymbol(SYMBOL_BTC_USDT);
        buyOrder2.setPrice(new BigDecimal("50000"));
        buyOrder2.setAmount(new BigDecimal("3"));
        orderService.placeOrder(buyOrder2);
        printOrderBook(SYMBOL_BTC_USDT);
        // 6️⃣ 查询买方和卖方的交易记录
        List<Trade> buyerTrades = tradeService.getTradesByUser(buyerId);
        List<Trade> sellerTrades = tradeService.getTradesByUser(sellerId);
        List<Trade> buyerTrades2 = tradeService.getTradesByUser(buyerId2);
        System.out.println("Buyer trades:");
        buyerTrades.forEach(System.out::println);

        System.out.println("Seller trades:");
        sellerTrades.forEach(System.out::println);

        System.out.println("Buyer trades2:");
        buyerTrades2.forEach(System.out::println);

        System.out.println("===============================");
        // 7️⃣ 查询买卖双方余额（可选）
        System.out.println("Buyer BTC balance: " + walletService.getBalance(buyerId, CoinConstant.BTC));
        System.out.println("Buyer USDT balance: " + walletService.getBalance(buyerId, CoinConstant.USDT));
        System.out.println("Seller USD balance: " + walletService.getBalance(sellerId, CoinConstant.USDT));
        System.out.println("Seller BTC balance: " + walletService.getBalance(sellerId, CoinConstant.BTC));

    }

    private void printOrderBook(String symbol) {
        // 等待Disruptor处理事件
        try {
            Thread.sleep(10000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Map<String, List<OrderBookLevel>> orderBook = orderService.getOrderBook(symbol);

        System.out.println("\n==== Order Book (" + symbol + ") ====");

        // 打印卖盘 (Asks)
        List<OrderBookLevel> asks = orderBook.get("asks");
        System.out.println("Asks (卖盘):");
        if (asks != null && !asks.isEmpty()) {
            asks.forEach(level -> System.out.println("  Price: " + level.getPrice() + ", Quantity: " + level.getQuantity()));
        } else {
            System.out.println("  (empty)");
        }

        // 打印买盘 (Bids)
        List<OrderBookLevel> bids = orderBook.get("bids");
        System.out.println("Bids (买盘):");
        if (bids != null && !bids.isEmpty()) {
            bids.forEach(level -> System.out.println("  Price: " + level.getPrice() + ", Quantity: " + level.getQuantity()));
        } else {
            System.out.println("  (empty)");
        }
        System.out.println("========================\n");
    }
}
