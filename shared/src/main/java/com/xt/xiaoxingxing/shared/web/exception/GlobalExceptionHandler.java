package com.xt.xiaoxingxing.shared.web.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import com.xt.xiaoxingxing.shared.core.response.Result;
import com.xt.xiaoxingxing.shared.core.response.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import java.lang.reflect.UndeclaredThrowableException;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        Result<Void> result = new Result<>();
        result.setCode(e.getCode());
        result.setMessage(e.getMessage());
        return result;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValidException(Exception e) {
        String message = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException ex) {
            message = ex.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(error -> error.getField() + ":" + error.getDefaultMessage())
                    .orElse(message);
        }
        log.warn("参数校验失败: {}", message);
        Result<Void> result = new Result<>();
        result.setCode(ResultCode.PARAM_ERROR.getCode());
        result.setMessage(message);
        return result;
    }

    /**
     * 处理 @RequestParam、@PathVariable 上的 @Min、@Positive 等方法参数校验。
     */
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public Result<Void> handleMethodValidationException(Exception e) {
        String message = e instanceof ConstraintViolationException ex
                ? ex.getConstraintViolations().stream()
                        .findFirst()
                        .map(ConstraintViolation::getMessage)
                        .orElse("参数校验失败")
                : "参数校验失败";
        log.warn("方法参数校验失败: {}", message);
        Result<Void> result = new Result<>();
        result.setCode(ResultCode.PARAM_ERROR.getCode());
        result.setMessage(message);
        return result;
    }

    @ExceptionHandler(BlockException.class)
    public Result<Void> handleBlockException(BlockException e) {
        if (e instanceof DegradeException) {
            log.warn("熔断降级: {}", e.getClass().getSimpleName());
            Result<Void> result = new Result<>();
            result.setCode(503);
            result.setMessage("服务熔断，请稍后重试");
            return result;
        }
        log.warn("限流被拦截: {}", e.getClass().getSimpleName());
        Result<Void> result = new Result<>();
        result.setCode(429);
        result.setMessage("请求过于频繁，请稍后重试");
        return result;
    }

    @ExceptionHandler(UndeclaredThrowableException.class)
    public Result<Void> handleUndeclaredThrowableException(UndeclaredThrowableException e) {
        Throwable cause = e.getCause();
        if (cause instanceof BlockException) {
            return handleBlockException((BlockException) cause);
        }
        log.error("反射调用异常（未识别的限流包装异常）: ", e);
        return Result.error(ResultCode.ERROR);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error(ResultCode.ERROR);
    }
}
