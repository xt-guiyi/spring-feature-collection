package com.xt.xiaoxingxing.playground.mongo.service.impl;

import com.xt.xiaoxingxing.playground.mongo.document.QuestionDefinition;
import com.xt.xiaoxingxing.playground.mongo.document.QuestionnaireDocument;
import com.xt.xiaoxingxing.playground.mongo.enums.QuestionType;
import com.xt.xiaoxingxing.playground.mongo.repository.QuestionnaireRepository;
import com.xt.xiaoxingxing.playground.mongo.repository.QuestionnaireSubmissionRepository;
import com.xt.xiaoxingxing.playground.mongo.service.MongoQuestionnaireStatisticsService;
import com.xt.xiaoxingxing.playground.mongo.vo.QuestionStatisticVO;
import com.xt.xiaoxingxing.playground.mongo.vo.QuestionnaireStatisticsVO;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** MongoDB 聚合管道学习案例。 */
@Service
@RequiredArgsConstructor
public class MongoQuestionnaireStatisticsServiceImpl implements MongoQuestionnaireStatisticsService {

    private static final String SUBMISSION_COLLECTION = "questionnaire_submissions";

    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireSubmissionRepository submissionRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public QuestionnaireStatisticsVO statistics(String questionnaireId) {
        /*
         * 实现步骤：
         * 1. 查询问卷定义，保证统计输出包含全部题目，包括无人作答的题目。
         * 2. Repository.count 统计答卷文档总数。
         * 3. 第一条聚合管道使用 $unwind + $group 统计每道题的作答数。
         * 4. 第二条聚合管道再次展开选择数组，统计每个选项出现次数。
         * 5. 第三条聚合管道对 NUMBER/RATING 的 value 计算 avg/min/max。
         * 6. 以问卷题目顺序为主线，把三组聚合结果组装成稳定的返回结构。
         */

        // 第1步：不能只根据答卷聚合结果生成题目，否则“0人作答”的题目会消失。
        BusinessAssert.hasText(questionnaireId, "问卷ID不能为空");
        QuestionnaireDocument questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new BusinessException("问卷不存在"));

        // 第2步：简单 count 使用 Repository，展示它与 MongoTemplate 聚合的自然分工。
        long totalSubmissions = submissionRepository.countByQuestionnaireId(questionnaireId);

        // 第3步：answers 是内嵌数组，必须先 $unwind 成多行才能按 questionId 分组。
        Map<String, Long> answeredCounts = aggregateAnsweredCounts(questionnaireId);

        // 第4步：单选也规范化为单元素数组，因此两种选择题可以共用第二次 $unwind。
        Map<String, Map<String, Long>> optionCounts = aggregateOptionCounts(questionnaireId);

        // 第5步：MongoDB 在服务端完成数值统计，不把全部答卷拉回 Java 再计算。
        Map<String, NumericStatistics> numericStatistics = aggregateNumericStatistics(questionnaireId);

        // 第6步：使用默认值表达“无人作答”，不让前端猜测缺失的聚合分组。
        List<QuestionStatisticVO> questionResults = questionnaire.getQuestions().stream()
                .map(question -> toStatistic(question, answeredCounts, optionCounts, numericStatistics))
                .toList();

        QuestionnaireStatisticsVO result = new QuestionnaireStatisticsVO();
        result.setQuestionnaireId(questionnaire.getId());
        result.setQuestionnaireTitle(questionnaire.getTitle());
        result.setTotalSubmissions(totalSubmissions);
        result.setQuestions(questionResults);
        return result;
    }

    private Map<String, Long> aggregateAnsweredCounts(String questionnaireId) {
        Aggregation aggregation = Aggregation.newAggregation(
                rawStage("$match", new Document("questionnaireId", questionnaireId)),
                rawStage("$unwind", "$answers"),
                rawStage("$group", new Document("_id", "$answers.questionId")
                        .append("total", new Document("$sum", 1))),
                rawStage("$project", new Document("_id", 0)
                        .append("questionId", "$_id")
                        .append("total", 1)));

        Map<String, Long> result = new LinkedHashMap<>();
        for (Document row : aggregate(aggregation)) {
            result.put(row.getString("questionId"), toLong(row.get("total")));
        }
        return result;
    }

    private Map<String, Map<String, Long>> aggregateOptionCounts(String questionnaireId) {
        List<String> choiceTypes = List.of(
                QuestionType.SINGLE_CHOICE.name(),
                QuestionType.MULTIPLE_CHOICE.name());
        Document groupId = new Document("questionId", "$answers.questionId")
                .append("option", "$answers.value");

        Aggregation aggregation = Aggregation.newAggregation(
                rawStage("$match", new Document("questionnaireId", questionnaireId)),
                rawStage("$unwind", "$answers"),
                rawStage("$match", new Document("answers.questionType", new Document("$in", choiceTypes))),
                rawStage("$unwind", "$answers.value"),
                rawStage("$group", new Document("_id", groupId)
                        .append("total", new Document("$sum", 1))),
                rawStage("$sort", new Document("_id.questionId", 1).append("total", -1)));

        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        for (Document row : aggregate(aggregation)) {
            Document id = row.get("_id", Document.class);
            String questionId = id.getString("questionId");
            String option = id.getString("option");
            result.computeIfAbsent(questionId, ignored -> new LinkedHashMap<>())
                    .put(option, toLong(row.get("total")));
        }
        return result;
    }

    private Map<String, NumericStatistics> aggregateNumericStatistics(String questionnaireId) {
        List<String> numericTypes = List.of(QuestionType.NUMBER.name(), QuestionType.RATING.name());
        Aggregation aggregation = Aggregation.newAggregation(
                rawStage("$match", new Document("questionnaireId", questionnaireId)),
                rawStage("$unwind", "$answers"),
                rawStage("$match", new Document("answers.questionType", new Document("$in", numericTypes))),
                rawStage("$group", new Document("_id", "$answers.questionId")
                        .append("average", new Document("$avg", "$answers.value"))
                        .append("minimum", new Document("$min", "$answers.value"))
                        .append("maximum", new Document("$max", "$answers.value"))));

        Map<String, NumericStatistics> result = new LinkedHashMap<>();
        for (Document row : aggregate(aggregation)) {
            result.put(row.getString("_id"), new NumericStatistics(
                    toBigDecimal(row.get("average")),
                    toBigDecimal(row.get("minimum")),
                    toBigDecimal(row.get("maximum"))));
        }
        return result;
    }

    /**
     * 直接构造阶段 Document，可以让学习者在 Java 旁边看到几乎原样的 Mongo Shell 管道结构。
     */
    private AggregationOperation rawStage(String operator, Object body) {
        return context -> new Document(operator, body);
    }

    private List<Document> aggregate(Aggregation aggregation) {
        return mongoTemplate.aggregate(aggregation, SUBMISSION_COLLECTION, Document.class)
                .getMappedResults();
    }

    private QuestionStatisticVO toStatistic(QuestionDefinition question,
                                            Map<String, Long> answeredCounts,
                                            Map<String, Map<String, Long>> optionCounts,
                                            Map<String, NumericStatistics> numericStatistics) {
        QuestionStatisticVO result = new QuestionStatisticVO();
        result.setQuestionId(question.getId());
        result.setQuestionTitle(question.getTitle());
        result.setQuestionType(question.getType());
        result.setTotalAnswered(answeredCounts.getOrDefault(question.getId(), 0L));
        result.setOptionCounts(new LinkedHashMap<>(optionCounts.getOrDefault(question.getId(), Map.of())));

        NumericStatistics numbers = numericStatistics.get(question.getId());
        if (numbers != null) {
            result.setAverage(numbers.average());
            result.setMinimum(numbers.minimum());
            result.setMaximum(numbers.maximum());
        }
        return result;
    }

    private long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Decimal128 decimal128) {
            return decimal128.bigDecimalValue();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private record NumericStatistics(BigDecimal average, BigDecimal minimum, BigDecimal maximum) {
    }
}
