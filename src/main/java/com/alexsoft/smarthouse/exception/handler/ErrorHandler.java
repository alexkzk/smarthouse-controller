package com.alexsoft.smarthouse.exception.handler;

import java.util.Collections;
import java.util.List;

import com.alexsoft.smarthouse.dto.ErrorResponseDto;
import com.alexsoft.smarthouse.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ErrorHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponseDto> handle(BadRequestException ex) {
        LOGGER.warn(ex.getMessage(), ex);
        return createResponseEntity(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<ErrorResponseDto> createResponseEntity(HttpStatus status, List<String> errors) {
        return ResponseEntity.status(status.value()).body(new ErrorResponseDto(errors));
    }

    private ResponseEntity<ErrorResponseDto> createResponseEntity(HttpStatus status, String error) {
        return createResponseEntity(status, Collections.singletonList(error));
    }

}
