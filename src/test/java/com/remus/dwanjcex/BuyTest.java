package com.remus.dwanjcex;

import com.remus.dwanjcex.common.AssetEnum;
import com.remus.dwanjcex.common.OrderTypes;
import com.remus.dwanjcex.common.SymbolEnum;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.Trade;
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


    @Resource
    private UserService userService;

    @Resource
    private WalletService walletService;

    @Resource
    private OrderService orderService;

    @Resource
    private TradeService tradeService;

    @Test
    public void t_buyBtc(){
        // 1️⃣ 注册买方和卖方
        Long buyerId = userService.genUser("admin"+ UUID.randomUUID().toString().substring(0,5), "123");
        Long sellerId = userService.genUser("admin"+ UUID.randomUUID().toString().substring(0,5), "123");

        System.out.println("Buyer ID: " + buyerId);
        System.out.println("Seller ID: " + sellerId);

        //买方充值100W USD

        walletService.deposit(buyerId,AssetEnum.USDT, new BigDecimal("1000000"),"Deposit");

        //卖方充值50BTC
        walletService.deposit(sellerId,AssetEnum.BTC,new BigDecimal("50"),"Deposit");
        // 2️⃣ 买方下单买 1 BTC，价格 50000 USD
        OrderDto buyOrder = new OrderDto();
        buyOrder.setUserId(buyerId);
        buyOrder.setSide(OrderTypes.Side.BUY);
        buyOrder.setSymbol(SymbolEnum.BTC_USDT);
        buyOrder.setPrice(new BigDecimal("50000"));
        buyOrder.setAmount(new BigDecimal("2"));
        orderService.placeOrder(buyOrder);

        System.out.println("Buy order placed: " + buyOrder);

        // 3️⃣ 查看当前订单簿
        Map<String, Map<BigDecimal, List<OrderEntity>>> orderBook = orderService.getOrderBook(SymbolEnum.BTC_USDT);
        System.out.println("Order Book:");
        printOrderBook(orderBook);

        // 4️⃣ 卖方下单卖 1 BTC，价格 50000 USD
        OrderDto sellOrder = new OrderDto();
        sellOrder.setUserId(sellerId);
        sellOrder.setSide(OrderTypes.Side.SELL);
        sellOrder.setSymbol(SymbolEnum.BTC_USDT);
        sellOrder.setPrice(new BigDecimal("50000"));
        sellOrder.setAmount(new BigDecimal("1"));
        orderService.placeOrder(sellOrder);

        System.out.println("Sell order placed: " + sellOrder);

//        卖方第二次下单卖 1 BTC，价格 50000 USD
        OrderDto sellOrder2 = new OrderDto();
        sellOrder2.setUserId(sellerId);
        sellOrder2.setSide(OrderTypes.Side.SELL);
        sellOrder2.setSymbol(SymbolEnum.BTC_USDT);
        sellOrder2.setPrice(new BigDecimal("50000"));
        sellOrder2.setAmount(new BigDecimal("1"));
        orderService.placeOrder(sellOrder2);

        // 5️⃣ 撮合成交（如果你的系统是自动撮合，这一步可能是内部逻辑）
//        matchingEngine.matchOrder("BTC");

        // 6️⃣ 查询买方和卖方的交易记录
        List<Trade> buyerTrades = tradeService.getTradesByUser(buyerId);
        List<Trade> sellerTrades = tradeService.getTradesByUser(sellerId);

        System.out.println("Buyer trades:");
        buyerTrades.forEach(System.out::println);

        System.out.println("Seller trades:");
        sellerTrades.forEach(System.out::println);

        // 7️⃣ 查询买卖双方余额（可选）
        System.out.println("Buyer BTC balance: " + walletService.getBalance(buyerId, AssetEnum.BTC));
        System.out.println("Seller USD balance: " + walletService.getBalance(sellerId, AssetEnum.USDT));

    }

    private void printOrderBook(Map<String, Map<BigDecimal, List<OrderEntity>>> orderBook) {
        System.out.println("==== Order Book ====");

        // 打印买盘
        Map<BigDecimal, List<OrderEntity>> bids = orderBook.get("bids");
        System.out.println("Bids:");
        if (bids != null && !bids.isEmpty()) {
            bids.entrySet().stream()
                    .sorted((e1, e2) -> e2.getKey().compareTo(e1.getKey())) // 买盘降序
                    .forEach(entry -> {
                        BigDecimal price = entry.getKey();
                        BigDecimal totalQty = entry.getValue().stream()
                                .map(OrderEntity::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        System.out.println("Price: " + price + ", Total Qty: " + totalQty
                                + ", Orders: " + entry.getValue().size());
                    });
        } else {
            System.out.println("No bids");
        }

        // 打印卖盘
        Map<BigDecimal, List<OrderEntity>> asks = orderBook.get("asks");
        System.out.println("Asks:");
        if (asks != null && !asks.isEmpty()) {
            asks.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()) // 卖盘升序
                    .forEach(entry -> {
                        BigDecimal price = entry.getKey();
                        BigDecimal totalQty = entry.getValue().stream()
                                .map(OrderEntity::getAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                        System.out.println("Price: " + price + ", Total Qty: " + totalQty
                                + ", Orders: " + entry.getValue().size());
                    });
        } else {
            System.out.println("No asks");
        }

        System.out.println("====================");
    }
}
