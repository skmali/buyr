package com.malison.catalogservice.controller.advice;

import com.malison.catalogservice.exception.ProductServiceException;
import com.malison.catalogservice.model.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class RestResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProductServiceException.class)
    public ResponseEntity<ErrorResponse> handleProductServiceException(ProductServiceException ex) {
        return new ResponseEntity<>(ErrorResponse.builder().message(ex.getMessage()).errorCode(ex.getErrorCode())
                .build(), HttpStatus.NOT_FOUND);
    }

}
