package com.xt.xiaoxingxing.playground.xxljob.support;

import com.xxl.job.core.context.XxlJobHelper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.util.stream.Collectors;

/** 解析并校验 XXL-JOB 的 JSON 任务参数。 */
@Component
public class XxlJobParamParser {

    private final JsonMapper jsonMapper;
    private final Validator validator;

    public XxlJobParamParser(JsonMapper jsonMapper, Validator validator) {
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    /** 读取当前任务参数并转换为指定 DTO。 */
    public <T> T parse(Class<T> parameterType) {
        // 1. 读取 Admin 配置的任务参数。
        String rawParam = XxlJobHelper.getJobParam();
        if (!StringUtils.hasText(rawParam)) {
            throw new IllegalArgumentException("XXL-JOB任务参数不能为空，期望JSON类型：" + parameterType.getSimpleName());
        }

        // 2. 将 JSON 转换为任务参数对象。
        final T parameter;
        try {
            parameter = jsonMapper.readValue(rawParam, parameterType);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "XXL-JOB任务参数不是合法的" + parameterType.getSimpleName()
                            + " JSON：" + exception.getOriginalMessage(),
                    exception
            );
        }

        // 3. 校验转换后的任务参数。
        if (parameter == null) {
            throw new IllegalArgumentException(
                    "XXL-JOB任务参数不能是JSON null，期望JSON对象：" + parameterType.getSimpleName()
            );
        }

        Set<ConstraintViolation<T>> violations = validator.validate(parameter);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(violation -> violation.getPropertyPath() + "：" + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining("；"));
            throw new IllegalArgumentException(
                    "XXL-JOB任务参数校验失败[" + parameterType.getSimpleName() + "]：" + detail
            );
        }
        return parameter;
    }
}
