package com.mi.goffer.common.convention.exception;


import com.mi.goffer.common.convention.errorcode.BaseErrorCode;
import com.mi.goffer.common.convention.errorcode.IErrorCode;

/**
 * @Author: TwentyFiveBTea
 * @Date: 2026/03/11 21:56
 * @Description: 客户端异常
 */
public class ClientException extends AbstractException {

    public ClientException(String message) {
        super(message, null, BaseErrorCode.CLIENT_ERROR);
    }

    public ClientException(IErrorCode errorCode) {
        super(null, null, errorCode);
    }

    public ClientException(String message, Throwable throwable) {
        super(message, throwable, BaseErrorCode.CLIENT_ERROR);
    }
}
