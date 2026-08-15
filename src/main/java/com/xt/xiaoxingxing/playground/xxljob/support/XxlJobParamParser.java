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

/**
 * 把 Admin 中配置的字符串任务参数统一转换为强类型 DTO。
 *
 * <p>XXL-JOB 只负责传递一段字符串，不理解业务 JSON，也不会自动执行 Jakarta Validation。
 * 如果每个 Handler 各自解析，空参数、JSON {@code null}、非法枚举和字段越界很容易产生不同的失败语义；
 * 本组件把这些情况统一转换为带 DTO 类型和字段原因的 {@link IllegalArgumentException}。</p>
 */
@Component
public class XxlJobParamParser {

    private final JsonMapper jsonMapper;
    private final Validator validator;

    public XxlJobParamParser(JsonMapper jsonMapper, Validator validator) {
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    /**
     * 读取当前调度参数、反序列化并执行 Bean Validation。
     *
     * @param parameterType 当前 Handler 所约定的参数 DTO
     * @return 已通过 JSON 和字段约束校验的参数对象
     * @throws IllegalArgumentException 参数为空、不是合法 JSON、反序列化结果为 JSON null 或字段校验失败
     */
    public <T> T parse(Class<T> parameterType) {
        String rawParam = XxlJobHelper.getJobParam();
        if (!StringUtils.hasText(rawParam)) {
            throw new IllegalArgumentException("XXL-JOB任务参数不能为空，期望JSON类型：" + parameterType.getSimpleName());
        }

        final T parameter;
        try {
            parameter = jsonMapper.readValue(rawParam, parameterType);
        } catch (JacksonException exception) {
            /*
             * Jackson 3 使用 JacksonException 统一表示 JSON 语法、类型转换和数据绑定失败。
             * 不把整段原始参数拼进异常，避免任务参数包含令牌等敏感内容时泄漏到调度日志；
             * getOriginalMessage() 只提取 Jackson 的核心原因，完整异常仍作为 cause 保留。
             */
            throw new IllegalArgumentException(
                    "XXL-JOB任务参数不是合法的" + parameterType.getSimpleName()
                            + " JSON：" + exception.getOriginalMessage(),
                    exception
            );
        }

        // 文本 "null" 是合法 JSON，却不能作为业务 DTO；必须与 Java 层的空参数一样明确拒绝。
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
