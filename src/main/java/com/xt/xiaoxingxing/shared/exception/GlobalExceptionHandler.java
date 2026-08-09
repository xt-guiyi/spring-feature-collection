package com.xt.xiaoxingxing.shared.exception;

import com.xt.xiaoxingxing.shared.common.Result;
import com.xt.xiaoxingxing.shared.enums.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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
     *
     * <p>MongoDB 问卷接口大量使用 expectedVersion 和分页参数，如果不单独处理，参数错误会落入
     * 通用异常分支并被误报为系统错误。</p>
     */
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public Result<Void> handleMethodValidationException(Exception e) {
        String message = e instanceof ConstraintViolationException ex
                ? ex.getConstraintViolations().stream()
                        .findFirst()
                        .map(violation -> violation.getMessage())
                        .orElse("参数校验失败")
                : "参数校验失败";
        log.warn("方法参数校验失败: {}", message);
        Result<Void> result = new Result<>();
        result.setCode(ResultCode.PARAM_ERROR.getCode());
        result.setMessage(message);
        return result;
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error(ResultCode.ERROR);
    }
}
