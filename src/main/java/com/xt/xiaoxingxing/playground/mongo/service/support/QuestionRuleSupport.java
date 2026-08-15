package com.xt.xiaoxingxing.playground.mongo.service.support;

import com.xt.xiaoxingxing.playground.mongo.document.QuestionDefinition;
import com.xt.xiaoxingxing.playground.mongo.enums.QuestionType;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态题型规则中心：创建题目时规范化 settings，提交答卷时把 JsonNode 转换成可持久化 BSON 值。
 */
@Component
public class QuestionRuleSupport {

    private static final int DEFAULT_TEXT_MAX_LENGTH = 1000;

    public Map<String, Object> normalizeSettings(QuestionType type, Map<String, Object> source) {
        BusinessAssert.notNull(type, "题目类型不能为空");
        Map<String, Object> input = source == null ? Map.of() : source;
        Map<String, Object> result = new LinkedHashMap<>();

        switch (type) {
            case TEXT -> result.put("maxLength",
                    readInteger(input, "maxLength", DEFAULT_TEXT_MAX_LENGTH, 1, 10000));
            case SINGLE_CHOICE -> result.put("options", readOptions(input));
            case MULTIPLE_CHOICE -> {
                List<String> options = readOptions(input);
                int minSelections = readInteger(input, "minSelections", 1, 1, options.size());
                int maxSelections = readInteger(input, "maxSelections", options.size(), minSelections, options.size());
                result.put("options", options);
                result.put("minSelections", minSelections);
                result.put("maxSelections", maxSelections);
            }
            case NUMBER -> {
                BigDecimal min = readOptionalDecimal(input, "min");
                BigDecimal max = readOptionalDecimal(input, "max");
                BusinessAssert.isTrue(min == null || max == null || min.compareTo(max) <= 0,
                        "NUMBER题的min不能大于max");
                if (min != null) {
                    result.put("min", min);
                }
                if (max != null) {
                    result.put("max", max);
                }
            }
            case RATING -> {
                int min = readInteger(input, "min", 1, 1, 100);
                int max = readInteger(input, "max", 5, min, 100);
                result.put("min", min);
                result.put("max", max);
            }
        }
        return result;
    }

    /** 前端答案统一从 JsonNode 进入，再按题型转换成 MongoDB 可直接保存的 Java 类型。 */
    public Object normalizeAnswer(QuestionDefinition question, JsonNode value) {
        BusinessAssert.notNull(value, "题目“" + question.getTitle() + "”的答案不能为空");
        return switch (question.getType()) {
            case TEXT -> normalizeText(question, value);
            case SINGLE_CHOICE -> normalizeSingleChoice(question, value);
            case MULTIPLE_CHOICE -> normalizeMultipleChoice(question, value);
            case NUMBER -> normalizeNumber(question, value);
            case RATING -> normalizeRating(question, value);
        };
    }

    private String normalizeText(QuestionDefinition question, JsonNode value) {
        BusinessAssert.isTrue(value.isTextual(), "题目“" + question.getTitle() + "”必须提交字符串");
        String text = value.textValue().trim();
        BusinessAssert.hasText(text, "题目“" + question.getTitle() + "”不能提交空白字符串");
        int maxLength = ((Number) question.getSettings().get("maxLength")).intValue();
        BusinessAssert.isTrue(text.length() <= maxLength,
                "题目“" + question.getTitle() + "”不能超过" + maxLength + "个字符");
        return text;
    }

    /** 单选也统一保存为单元素数组，聚合时单选和多选可以共用一次 $unwind。 */
    private List<String> normalizeSingleChoice(QuestionDefinition question, JsonNode value) {
        List<String> selected;
        if (value.isTextual()) {
            selected = List.of(value.textValue());
        } else {
            selected = readStringArray(value, question.getTitle());
        }
        BusinessAssert.isTrue(selected.size() == 1, "题目“" + question.getTitle() + "”只能选择一个选项");
        validateAllowedOptions(question, selected);
        return selected;
    }

    private List<String> normalizeMultipleChoice(QuestionDefinition question, JsonNode value) {
        List<String> selected = readStringArray(value, question.getTitle());
        int min = ((Number) question.getSettings().get("minSelections")).intValue();
        int max = ((Number) question.getSettings().get("maxSelections")).intValue();
        BusinessAssert.isTrue(selected.size() >= min && selected.size() <= max,
                "题目“" + question.getTitle() + "”选择数量必须在" + min + "到" + max + "之间");
        validateAllowedOptions(question, selected);
        return selected;
    }

    private BigDecimal normalizeNumber(QuestionDefinition question, JsonNode value) {
        BusinessAssert.isTrue(value.isNumber(), "题目“" + question.getTitle() + "”必须提交JSON数字");
        BigDecimal number = value.decimalValue();
        Object minValue = question.getSettings().get("min");
        Object maxValue = question.getSettings().get("max");
        if (minValue != null) {
            BusinessAssert.isTrue(number.compareTo(toBigDecimal(minValue, "min")) >= 0,
                    "题目“" + question.getTitle() + "”不能小于" + minValue);
        }
        if (maxValue != null) {
            BusinessAssert.isTrue(number.compareTo(toBigDecimal(maxValue, "max")) <= 0,
                    "题目“" + question.getTitle() + "”不能大于" + maxValue);
        }
        return number;
    }

    private Integer normalizeRating(QuestionDefinition question, JsonNode value) {
        BusinessAssert.isTrue(value.isIntegralNumber(), "题目“" + question.getTitle() + "”必须提交整数评分");
        int rating = value.intValue();
        int min = ((Number) question.getSettings().get("min")).intValue();
        int max = ((Number) question.getSettings().get("max")).intValue();
        BusinessAssert.isTrue(rating >= min && rating <= max,
                "题目“" + question.getTitle() + "”评分必须在" + min + "到" + max + "之间");
        return rating;
    }

    private List<String> readOptions(Map<String, Object> source) {
        Object raw = source.get("options");
        BusinessAssert.isTrue(raw instanceof Collection<?>, "选择题settings.options必须是数组");
        List<String> options = ((Collection<?>) raw).stream()
                .map(value -> value == null ? "" : value.toString().trim())
                .toList();
        BusinessAssert.isTrue(options.size() >= 2, "选择题至少需要两个选项");
        BusinessAssert.isTrue(options.stream().allMatch(value -> !value.isBlank()), "选择题选项不能是空字符串");
        BusinessAssert.isTrue(new LinkedHashSet<>(options).size() == options.size(), "选择题选项不能重复");
        return new ArrayList<>(options);
    }

    private List<String> readStringArray(JsonNode value, String title) {
        BusinessAssert.isTrue(value.isArray(), "题目“" + title + "”必须提交选项数组");
        List<String> selected = new ArrayList<>();
        value.forEach(node -> {
            BusinessAssert.isTrue(node.isTextual(), "题目“" + title + "”的选项必须是字符串");
            selected.add(node.textValue());
        });
        Set<String> distinct = new LinkedHashSet<>(selected);
        BusinessAssert.isTrue(distinct.size() == selected.size(), "题目“" + title + "”不能重复选择同一选项");
        return selected;
    }

    @SuppressWarnings("unchecked")
    private void validateAllowedOptions(QuestionDefinition question, List<String> selected) {
        List<String> allowed = (List<String>) question.getSettings().get("options");
        BusinessAssert.isTrue(allowed.containsAll(selected), "题目“" + question.getTitle() + "”包含不存在的选项");
    }

    private int readInteger(Map<String, Object> source, String key, int defaultValue, int min, int max) {
        Object raw = source.get(key);
        int value = raw == null ? defaultValue : toInteger(raw, key);
        BusinessAssert.isTrue(value >= min && value <= max,
                key + "必须在" + min + "到" + max + "之间");
        return value;
    }

    private int toInteger(Object value, String field) {
        try {
            if (value instanceof Number number) {
                return new BigDecimal(number.toString()).intValueExact();
            }
            return new BigDecimal(value.toString()).intValueExact();
        } catch (RuntimeException ex) {
            throw new com.xt.xiaoxingxing.shared.exception.BusinessException(field + "必须是整数");
        }
    }

    private BigDecimal readOptionalDecimal(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value == null ? null : toBigDecimal(value, key);
    }

    private BigDecimal toBigDecimal(Object value, String field) {
        try {
            return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
        } catch (RuntimeException ex) {
            throw new com.xt.xiaoxingxing.shared.exception.BusinessException(field + "必须是数字");
        }
    }
}
