package com.remus.dwanjcex.wallet.controller;

import com.remus.dwanjcex.config.jwt.UserContextHolder;
import com.remus.dwanjcex.exception.BusinessException;
import com.remus.dwanjcex.wallet.entity.OrderEntity;
import com.remus.dwanjcex.wallet.entity.dto.OrderDto;
import com.remus.dwanjcex.wallet.entity.result.ResponseResult;
import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import com.remus.dwanjcex.wallet.services.OrderService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order") // 将路径移动到/api/下，以被JWT过滤器保护
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/place")
    public ResponseResult<?> place(@RequestBody OrderDto dto) {
        try {
            // 从UserContextHolder获取当前登录的用户ID
            Long currentUserId = UserContextHolder.getCurrentUserId();
            if (currentUserId == null) {
                // 这通常意味着JWT无效或未提供
                return ResponseResult.error(ResultCode.UNAUTHORIZED);
            }
            // 将DTO中的userId强制设置为当前登录的用户ID，防止伪造
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
            // 从UserContextHolder获取当前登录的用户ID
            Long currentUserId = UserContextHolder.getCurrentUserId();
            if (currentUserId == null) {
                return ResponseResult.error(ResultCode.UNAUTHORIZED);
            }

            // 将用户ID传递给service层进行权限校验
            orderService.cancelOrder(currentUserId, orderId);
            return ResponseResult.success();
        } catch (BusinessException e) {
            return ResponseResult.error(e.getResultCode());
        }
    }
}
