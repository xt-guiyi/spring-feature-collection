package com.xt.xiaoxingxing.playground.mongo.service.impl;

import com.xt.xiaoxingxing.playground.mongo.document.AnswerSnapshot;
import com.xt.xiaoxingxing.playground.mongo.document.QuestionDefinition;
import com.xt.xiaoxingxing.playground.mongo.document.QuestionnaireDocument;
import com.xt.xiaoxingxing.playground.mongo.document.QuestionnaireSubmissionDocument;
import com.xt.xiaoxingxing.playground.mongo.dto.request.AnswerRequest;
import com.xt.xiaoxingxing.playground.mongo.dto.request.SubmissionCreateRequest;
import com.xt.xiaoxingxing.playground.mongo.enums.QuestionnaireStatus;
import com.xt.xiaoxingxing.playground.mongo.repository.QuestionnaireRepository;
import com.xt.xiaoxingxing.playground.mongo.repository.QuestionnaireSubmissionRepository;
import com.xt.xiaoxingxing.playground.mongo.service.MongoSubmissionService;
import com.xt.xiaoxingxing.playground.mongo.service.support.PostgresUserLookup;
import com.xt.xiaoxingxing.playground.mongo.service.support.QuestionRuleSupport;
import com.xt.xiaoxingxing.playground.mongo.vo.AnswerVO;
import com.xt.xiaoxingxing.playground.mongo.vo.SubmissionVO;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgUser;
import com.xt.xiaoxingxing.shared.common.PageResult;
import com.xt.xiaoxingxing.shared.exception.BusinessException;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 答卷提交、查询和 PostgreSQL 用户信息组装。 */
@Service
@RequiredArgsConstructor
public class MongoSubmissionServiceImpl implements MongoSubmissionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final QuestionnaireRepository questionnaireRepository;
    private final QuestionnaireSubmissionRepository submissionRepository;
    private final MongoTemplate mongoTemplate;
    private final PostgresUserLookup postgresUserLookup;
    private final QuestionRuleSupport questionRuleSupport;

    @Override
    public SubmissionVO submit(String questionnaireId, SubmissionCreateRequest request) {
        /*
         * 实现步骤：
         * 1. 查询 MongoDB 问卷并确认状态为 PUBLISHED。
         * 2. 到 PostgreSQL users 表校验答题用户存在且为 ACTIVE。
         * 3. Repository 预检查是否提交过，尽早返回友好错误。
         * 4. 建立题目Map和请求答案Map，校验未知题目、重复答案及必答题。
         * 5. 按问卷题目顺序规范化动态 value，并保存题目标题和类型快照。
         * 6. 使用 Repository.insert 保存答卷；唯一复合索引兜底并发重复提交。
         * 7. 组装 MongoDB 答卷和 PostgreSQL 用户摘要返回前端。
         */

        // 第1步：答卷写入前只读取问卷；本案例不启用跨集合 MongoDB 事务。
        validateId(questionnaireId, "问卷ID");
        QuestionnaireDocument questionnaire = questionnaireRepository.findById(questionnaireId)
                .orElseThrow(() -> new BusinessException("问卷不存在"));
        BusinessAssert.isTrue(questionnaire.getStatus() == QuestionnaireStatus.PUBLISHED,
                "只有PUBLISHED问卷可以提交答卷");

        // 第2步：用户主数据始终以 PostgreSQL 为准，MongoDB 不复制用户账号字段。
        PgUser user = postgresUserLookup.requireActive(request.getUserId());

        // 第3步：预检查不能代替唯一索引，因为两个并发请求可能同时看到“不存在”。
        BusinessAssert.isTrue(!submissionRepository.existsByQuestionnaireIdAndUserId(
                questionnaireId, user.getId()), "该用户已经提交过此问卷");

        // 第4步：Map 同时用于 O(1) 定位和检测重复 questionId。
        Map<String, QuestionDefinition> questionMap = questionnaire.getQuestions().stream()
                .collect(Collectors.toMap(
                        QuestionDefinition::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, AnswerRequest> answerMap = new LinkedHashMap<>();
        for (AnswerRequest answer : request.getAnswers()) {
            BusinessAssert.notNull(answer, "答案项不能为空");
            BusinessAssert.hasText(answer.getQuestionId(), "题目ID不能为空");
            BusinessAssert.isTrue(questionMap.containsKey(answer.getQuestionId()),
                    "答案包含当前问卷不存在的题目: " + answer.getQuestionId());
            BusinessAssert.isTrue(answerMap.put(answer.getQuestionId(), answer) == null,
                    "同一道题不能重复提交答案: " + answer.getQuestionId());
        }
        questionnaire.getQuestions().stream()
                .filter(QuestionDefinition::isRequired)
                .forEach(question -> BusinessAssert.isTrue(answerMap.containsKey(question.getId()),
                        "必答题未填写: " + question.getTitle()));

        // 第5步：迭代问卷题目而不是请求顺序，使文档中的答案顺序稳定且便于阅读。
        List<AnswerSnapshot> snapshots = new ArrayList<>();
        for (QuestionDefinition question : questionnaire.getQuestions()) {
            AnswerRequest answer = answerMap.get(question.getId());
            if (answer == null) {
                continue;
            }
            AnswerSnapshot snapshot = new AnswerSnapshot();
            snapshot.setQuestionId(question.getId());
            snapshot.setQuestionTitle(question.getTitle());
            snapshot.setQuestionType(question.getType());
            snapshot.setValue(questionRuleSupport.normalizeAnswer(question, answer.getValue()));
            snapshots.add(snapshot);
        }

        // 第6步：insert 触发 (questionnaireId,userId) 唯一索引；竞争失败统一转为业务异常。
        QuestionnaireSubmissionDocument document = new QuestionnaireSubmissionDocument();
        document.setQuestionnaireId(questionnaire.getId());
        document.setQuestionnaireTitle(questionnaire.getTitle());
        document.setUserId(user.getId());
        document.setAnswers(snapshots);
        QuestionnaireSubmissionDocument saved;
        try {
            saved = submissionRepository.insert(document);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("该用户已经提交过此问卷");
        }

        // 第7步：前端看到的是统一VO，而不是带MongoDB内部细节的Document实体。
        return toVO(saved, user);
    }

    @Override
    public SubmissionVO getById(String id) {
        validateId(id, "答卷ID");
        QuestionnaireSubmissionDocument document = submissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("答卷不存在"));
        Map<Long, PgUser> users = postgresUserLookup.findMap(List.of(document.getUserId()));
        return toVO(document, users.get(document.getUserId()));
    }

    @Override
    public PageResult<SubmissionVO> pageByQuestionnaire(String questionnaireId, int pageNum, int pageSize) {
        /*
         * 实现步骤：
         * 1. 确认问卷存在并校验分页参数。
         * 2. 使用 MongoTemplate 按 questionnaireId 统计并分页查询答卷。
         * 3. 收集当前页所有 userId，一次查询 PostgreSQL。
         * 4. 使用用户Map组装答卷VO，避免 N+1。
         */

        // 第1步：不存在的问卷与“存在但暂时没有答卷”应返回不同语义。
        validateId(questionnaireId, "问卷ID");
        BusinessAssert.isTrue(questionnaireRepository.existsById(questionnaireId), "问卷不存在");
        validatePage(pageNum, pageSize);

        // 第2步：等价 Mongo Shell：find({questionnaireId}).sort({submittedAt:-1,_id:-1}).skip().limit()。
        Query query = Query.query(Criteria.where("questionnaireId").is(questionnaireId));
        return queryPage(query, pageNum, pageSize);
    }

    @Override
    public PageResult<SubmissionVO> pageByUser(Long userId, int pageNum, int pageSize) {
        BusinessAssert.isTrue(userId != null && userId > 0, "用户ID必须大于0");
        validatePage(pageNum, pageSize);
        Query query = Query.query(Criteria.where("userId").is(userId));
        return queryPage(query, pageNum, pageSize);
    }

    private PageResult<SubmissionVO> queryPage(Query query, int pageNum, int pageSize) {
        long total = mongoTemplate.count(query, QuestionnaireSubmissionDocument.class);
        query.with(Sort.by(Sort.Order.desc("submittedAt"), Sort.Order.desc("_id")))
                .skip((long) (pageNum - 1) * pageSize)
                .limit(pageSize);
        List<QuestionnaireSubmissionDocument> documents = mongoTemplate.find(
                query, QuestionnaireSubmissionDocument.class);

        // 第3步：selectBatchIds 的参数先去重，避免生成重复 IN 值。
        Map<Long, PgUser> users = postgresUserLookup.findMap(documents.stream()
                .map(QuestionnaireSubmissionDocument::getUserId)
                .toList());

        // 第4步：PostgreSQL 用户已不存在时仍返回历史答卷，user 字段为 null。
        List<SubmissionVO> list = documents.stream()
                .map(document -> toVO(document, users.get(document.getUserId())))
                .toList();
        return pageResult(list, total, pageNum, pageSize);
    }

    private SubmissionVO toVO(QuestionnaireSubmissionDocument document, PgUser user) {
        SubmissionVO result = new SubmissionVO();
        result.setId(document.getId());
        result.setQuestionnaireId(document.getQuestionnaireId());
        result.setQuestionnaireTitle(document.getQuestionnaireTitle());
        result.setUser(postgresUserLookup.toSummary(user));
        result.setAnswers(document.getAnswers() == null ? List.of() : document.getAnswers().stream()
                .map(this::toAnswerVO)
                .toList());
        result.setSubmittedAt(document.getSubmittedAt());
        return result;
    }

    private AnswerVO toAnswerVO(AnswerSnapshot answer) {
        AnswerVO result = new AnswerVO();
        result.setQuestionId(answer.getQuestionId());
        result.setQuestionTitle(answer.getQuestionTitle());
        result.setQuestionType(answer.getQuestionType());
        result.setValue(answer.getValue());
        return result;
    }

    private <T> PageResult<T> pageResult(List<T> list, long total, int pageNum, int pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    private void validatePage(int pageNum, int pageSize) {
        BusinessAssert.isTrue(pageNum >= 1, "页码必须大于等于1");
        BusinessAssert.isTrue(pageSize >= 1 && pageSize <= MAX_PAGE_SIZE, "每页数量必须在1到100之间");
    }

    private void validateId(String id, String fieldName) {
        BusinessAssert.hasText(id, fieldName + "不能为空");
    }
}
