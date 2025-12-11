package com.remus.dwanjcex.engine;

import com.remus.dwanjcex.wallet.entity.OrderEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.LinkedList;

/**
 * 价格桶 (Price Bucket)。
 * 代表订单簿中的一个价格档位，包含该价格下的所有订单。
 */
@Getter
@Setter
@NoArgsConstructor
public class OrderBucket {

    private long price; // 价格 (定点数，例如 50000 * 10^8)
    private BigDecimal totalAmount = BigDecimal.ZERO; // 该档位的总数量
    private LinkedList<OrderEntity> orders = new LinkedList<>(); // 订单链表 (FIFO)

    public OrderBucket(long price) {
        this.price = price;
    }

    public void add(OrderEntity order) {
        orders.add(order);
        totalAmount = totalAmount.add(order.getRemaining());
    }

    public boolean remove(OrderEntity order) {
        boolean removed = orders.remove(order);
        if (removed) {
            totalAmount = totalAmount.subtract(order.getRemaining());
        }
        return removed;
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public OrderEntity peek() {
        return orders.peek();
    }

    public OrderEntity poll() {
        OrderEntity order = orders.poll();
        if (order != null) {
            totalAmount = totalAmount.subtract(order.getRemaining());
        }
        return order;
    }
}
