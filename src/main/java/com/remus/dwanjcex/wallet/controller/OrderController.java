package com.remus.dwanjcex.wallet.controller;

import com.remus.dwanjcex.config.jwt.UserContextHolder;
import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import com.remus.dwanjcex.wallet.entity.result.ResponseResult;
import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import com.remus.dwanjcex.wallet.services.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/place")
    public ResponseResult<?> place(@RequestBody OrderDto dto) {
        try {
            Long currentUserId = UserContextHolder.getCurrentUserId();
            if (currentUserId == null) {
                return ResponseResult.error(ResultCode.UNAUTHORIZED);
            }
            dto.setUserId(currentUserId);
            OrderEntity order = orderService.placeOrder(dto);
            return ResponseResult.success(order);
        } catch (BusinessException e) {
            return ResponseResult.error(e.getResultCode());
        }
    }

    @PostMapping("/cancel/{orderId}")
    public ResponseResult<?> cancel(@PathVariable Long orderId) {
        try {
            Long currentUserId = UserContextHolder.getCurrentUserId();
            if (currentUserId == null) {
                return ResponseResult.error(ResultCode.UNAUTHORIZED);
            }
            orderService.cancelOrder(currentUserId, orderId);
            return ResponseResult.success();
        } catch (BusinessException e) {
            return ResponseResult.error(e.getResultCode());
        }
    }

    @GetMapping("/my-orders")
    public ResponseResult<List<OrderEntity>> getMyOrders() {
        Long currentUserId = UserContextHolder.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseResult.error(ResultCode.UNAUTHORIZED);
        }
        List<OrderEntity> orders = orderService.getMyOrders(currentUserId);
        return ResponseResult.success(orders);
    }
}
