package com.remus.dwanjcex.wallet.entity.result;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应对象
 * @param <T> 返回数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseResult<T> {
    private int code;
    private String message;
    private T data;

    // 成功
    public static <T> ResponseResult<T> success() {
        return new ResponseResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> ResponseResult<T> success(T data) {
        return new ResponseResult<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> ResponseResult<T> success(String message, T data) {
        return new ResponseResult<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    // 错误
    public static <T> ResponseResult<T> error() {
        return new ResponseResult<>(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMessage(), null);
    }

    public static <T> ResponseResult<T> error(ResultCode resultCode) {
        return new ResponseResult<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> ResponseResult<T> error(ResultCode resultCode, String message) {
        return new ResponseResult<>(resultCode.getCode(), message, null);
    }

    public static <T> ResponseResult<T> error(int code, String message) {
        return new ResponseResult<>(code, message, null);
    }
}