package com.remus.dwanjcex.wallet.controller;

import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import com.remus.dwanjcex.wallet.entity.result.ResponseResult;
import com.remus.dwanjcex.wallet.services.OrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/place")
    public ResponseResult<?> place(@RequestBody OrderDto dto) {
        try {
            OrderEntity order = orderService.placeOrder(dto);
            return ResponseResult.success(order);
        } catch (BusinessException e) {
            return ResponseResult.error(e.getResultCode());
        }
    }

    @PostMapping("/cancel/{id}")
    public ResponseResult<?> cancel(@PathVariable Long id) {
        try {
            orderService.cancelOrder(id);
            return ResponseResult.success();
        } catch (BusinessException e) {
            return ResponseResult.error(e.getResultCode());
        }
    }
}