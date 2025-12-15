package com.remus.dwanjcex.exception;

import com.remus.dwanjcex.wallet.entity.result.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException{

   private final ResultCode resultCode;
   private  String error;

    public BusinessException(String message) {
        super(message);
        this.error = message;
        this.resultCode = ResultCode.ERROR;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }


}
