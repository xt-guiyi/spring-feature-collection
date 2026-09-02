package com.xt.xiaoxingxing.playground.features.mongo.service.impl;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.xt.xiaoxingxing.playground.features.mongo.document.QuestionDefinition;
import com.xt.xiaoxingxing.playground.features.mongo.document.QuestionnaireDocument;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionCreateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionUpdateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionnaireCreateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionnaireQueryRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionnaireUpdateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.enums.QuestionnaireStatus;
import com.xt.xiaoxingxing.playground.features.mongo.repository.QuestionnaireRepository;
import com.xt.xiaoxingxing.playground.features.mongo.service.MongoQuestionnaireService;
import com.xt.xiaoxingxing.playground.features.mongo.service.support.PostgresUserLookup;
import com.xt.xiaoxingxing.playground.features.mongo.service.support.QuestionRuleSupport;
import com.xt.xiaoxingxing.playground.features.mongo.dto.response.QuestionResponse;
import com.xt.xiaoxingxing.playground.features.mongo.dto.response.QuestionnaireResponse;
import com.xt.xiaoxingxing.playground.features.postgresql.entity.PgUser;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.exception.BusinessException;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 问卷学习实现：Repository 展示完整文档 CRUD，MongoTemplate 展示动态查询和数组原子更新。
 */
@Service
@RequiredArgsConstructor
public class MongoQuestionnaireServiceImpl implements MongoQuestionnaireService {

    private static final int MAX_PAGE_SIZE = 100;

    private final QuestionnaireRepository questionnaireRepository;
    private final MongoTemplate mongoTemplate;
    private final PostgresUserLookup postgresUserLookup;
    private final QuestionRuleSupport questionRuleSupport;

    @Override
    public QuestionnaireResponse create(QuestionnaireCreateRequest request) {
        /*
         * 实现步骤：
         * 1. 到 playground 自己的 PostgreSQL users 表验证创建人真实存在并且状态为 ACTIVE。
         * 2. 构造 MongoDB 问卷聚合根，明确初始状态只能是 DRAFT。
         * 3. 使用 MongoRepository.insert 保存完整文档，并返回本地用户摘要。
         */

        // 第1步：这里只读取 playground 自己的 PostgreSQL，不建立跨服务关联。
        PgUser creator = postgresUserLookup.requireActive(request.getCreatedByUserId());

        // 第2步：问题数组先保持为空，后续通过 $push 学习接口逐步添加。
        QuestionnaireDocument document = new QuestionnaireDocument();
        document.setTitle(request.getTitle().trim());
        document.setDescription(trimToNull(request.getDescription()));
        document.setCreatedByUserId(creator.getId());
        document.setStatus(QuestionnaireStatus.DRAFT);
        document.setQuestions(new ArrayList<>());

        // 第3步：insert 只允许新增；与 save 的“有ID时可能覆盖”语义不同。
        QuestionnaireDocument saved = questionnaireRepository.insert(document);
        return toResponse(saved, creator);
    }

    @Override
    public QuestionnaireResponse getById(String id) {
        QuestionnaireDocument document = requireQuestionnaire(id);
        Map<Long, PgUser> users = postgresUserLookup.findMap(List.of(document.getCreatedByUserId()));
        return toResponse(document, users.get(document.getCreatedByUserId()));
    }

    @Override
    public PageResult<QuestionnaireResponse> page(QuestionnaireQueryRequest request) {
        /*
         * 实现步骤：
         * 1. 根据非空查询参数动态组装 MongoDB Criteria。
         * 2. 先 count 统计总数，再应用 sort、skip、limit 查询当前页文档。
         * 3. 收集当前页创建人ID，一次批量查询 playground 的 PostgreSQL users，避免 N+1。
         * 4. 按 userId Map 组装 MongoDB 问卷与本地用户摘要。
         */

        // 第1步：相当于 Mongo Shell 的 find({status: ..., $or: [...]})。
        validatePage(request.getPageNum(), request.getPageSize());
        Query query = new Query();
        if (BusinessAssert.hasText(request.getKeyword())) {
            Pattern keyword = Pattern.compile(Pattern.quote(request.getKeyword().trim()), Pattern.CASE_INSENSITIVE);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex(keyword),
                    Criteria.where("description").regex(keyword)));
        }
        if (request.getStatus() != null) {
            query.addCriteria(Criteria.where("status").is(request.getStatus()));
        }
        if (request.getCreatedByUserId() != null) {
            BusinessAssert.isTrue(request.getCreatedByUserId() > 0, "创建人ID必须大于0");
            query.addCriteria(Criteria.where("createdByUserId").is(request.getCreatedByUserId()));
        }

        // 第2步：先执行 count；之后再给同一个 Query 添加分页，不影响已经完成的计数。
        long total = mongoTemplate.count(query, QuestionnaireDocument.class);
        long offset = (long) (request.getPageNum() - 1) * request.getPageSize();
        query.with(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("_id")))
                .skip(offset)
                .limit(request.getPageSize());
        List<QuestionnaireDocument> documents = mongoTemplate.find(query, QuestionnaireDocument.class);

        // 第3步：只查询当前页实际出现的创建人。
        Map<Long, PgUser> users = postgresUserLookup.findMap(documents.stream()
                .map(QuestionnaireDocument::getCreatedByUserId)
                .toList());

        // 第4步：历史用户缺失时 createdBy 返回 null，但问卷仍保留。
        List<QuestionnaireResponse> list = documents.stream()
                .map(document -> toResponse(document, users.get(document.getCreatedByUserId())))
                .toList();
        return pageResult(list, total, request.getPageNum(), request.getPageSize());
    }

    @Override
    public QuestionnaireResponse update(String id, long expectedVersion, QuestionnaireUpdateRequest request) {
        validateExpectedVersion(expectedVersion);
        Query query = draftMutationQuery(id, expectedVersion);
        Update update = new Update()
                .set("title", request.getTitle().trim())
                .set("description", trimToNull(request.getDescription()))
                .currentDate("updatedAt")
                .inc("version", 1L);
        requireUpdated(mongoTemplate.updateFirst(query, update, QuestionnaireDocument.class),
                id, expectedVersion, QuestionnaireStatus.DRAFT, null);
        return getById(id);
    }

    @Override
    public QuestionnaireResponse addQuestion(String id, long expectedVersion, QuestionCreateRequest request) {
        /*
         * 实现步骤：
         * 1. 校验并规范化动态 settings，使同一题型在数据库中的字段形态一致。
         * 2. 为内嵌题目生成 UUID，构造完整 QuestionDefinition。
         * 3. 通过 DRAFT + version 条件和 $push 原子追加数组元素，同时递增版本。
         * 4. 更新失败时重新读取文档，区分不存在、状态错误和版本冲突。
         */

        // 第1步：只保留当前题型支持的设置，未知设置不会静默进入数据库。
        validateExpectedVersion(expectedVersion);
        Map<String, Object> settings = questionRuleSupport.normalizeSettings(request.getType(), request.getSettings());

        // 第2步：内嵌题目没有 MongoDB _id，因此使用独立业务ID。
        QuestionDefinition question = new QuestionDefinition();
        question.setId(UUID.randomUUID().toString());
        question.setTitle(request.getTitle().trim());
        question.setType(request.getType());
        question.setRequired(Boolean.TRUE.equals(request.getRequired()));
        question.setSettings(settings);

        // 第3步：等价 Mongo Shell：updateOne({_id, status:'DRAFT', version}, {$push:{questions:...},$inc:{version:1}})。
        Update update = new Update()
                .push("questions", question)
                .currentDate("updatedAt")
                .inc("version", 1L);
        UpdateResult result = mongoTemplate.updateFirst(
                draftMutationQuery(id, expectedVersion), update, QuestionnaireDocument.class);

        // 第4步：条件写入比“先判断再保存整个文档”更能避免覆盖其他请求的修改。
        requireUpdated(result, id, expectedVersion, QuestionnaireStatus.DRAFT, null);
        return getById(id);
    }

    @Override
    public QuestionnaireResponse updateQuestion(String id, String questionId, long expectedVersion,
                                          QuestionUpdateRequest request) {
        /*
         * 实现步骤：
         * 1. 规范化题目设置并保留原 questionId。
         * 2. 查询条件同时锁定问卷ID、草稿状态、版本和 questions.id。
         * 3. 使用位置运算符 questions.$ 只替换命中的数组元素。
         * 4. 根据条件写入结果诊断题目不存在或并发版本冲突。
         */

        // 第1步：PUT 是完整替换题目内容，但不会改变已经分配的业务ID。
        validateExpectedVersion(expectedVersion);
        BusinessAssert.hasText(questionId, "题目ID不能为空");
        QuestionDefinition question = new QuestionDefinition();
        question.setId(questionId);
        question.setTitle(request.getTitle().trim());
        question.setType(request.getType());
        question.setRequired(Boolean.TRUE.equals(request.getRequired()));
        question.setSettings(questionRuleSupport.normalizeSettings(request.getType(), request.getSettings()));

        // 第2步：questions.id 条件让位置运算符 $ 明确指向要更新的题目。
        Query query = draftMutationQuery(id, expectedVersion);
        query.addCriteria(Criteria.where("questions.id").is(questionId));

        // 第3步：不会把整个 questions 数组读回 Java 后再整体覆盖。
        Update update = new Update()
                .set("questions.$", question)
                .currentDate("updatedAt")
                .inc("version", 1L);

        // 第4步：matchedCount 为0时检查当前真实状态，返回可理解的学习提示。
        requireUpdated(mongoTemplate.updateFirst(query, update, QuestionnaireDocument.class),
                id, expectedVersion, QuestionnaireStatus.DRAFT, questionId);
        return getById(id);
    }

    @Override
    public QuestionnaireResponse deleteQuestion(String id, String questionId, long expectedVersion) {
        validateExpectedVersion(expectedVersion);
        BusinessAssert.hasText(questionId, "题目ID不能为空");
        Query query = draftMutationQuery(id, expectedVersion);
        query.addCriteria(Criteria.where("questions.id").is(questionId));
        Update update = new Update()
                .pull("questions", Query.query(Criteria.where("id").is(questionId)))
                .currentDate("updatedAt")
                .inc("version", 1L);
        requireUpdated(mongoTemplate.updateFirst(query, update, QuestionnaireDocument.class),
                id, expectedVersion, QuestionnaireStatus.DRAFT, questionId);
        return getById(id);
    }

    @Override
    public QuestionnaireResponse publish(String id, long expectedVersion) {
        /*
         * 实现步骤：
         * 1. 读取当前问卷，给“空问卷不能发布”提供明确业务错误。
         * 2. 使用 DRAFT + version + questions.0 exists 条件完成原子状态迁移。
         * 3. 同时写入发布时间、更新时间并递增乐观锁版本。
         */

        // 第1步：预读只用于友好校验，最终正确性仍由下一步条件更新保证。
        validateExpectedVersion(expectedVersion);
        QuestionnaireDocument current = requireQuestionnaire(id);
        BusinessAssert.isTrue(current.getStatus() == QuestionnaireStatus.DRAFT, "只有DRAFT问卷可以发布");
        BusinessAssert.isTrue(current.getQuestions() != null && !current.getQuestions().isEmpty(), "空问卷不能发布");

        // 第2步：即使预读后发生并发修改，version 条件也会阻止使用旧版本发布。
        Query query = draftMutationQuery(id, expectedVersion);
        query.addCriteria(Criteria.where("questions.0").exists(true));

        // 第3步：状态迁移和时间写入属于同一个 MongoDB 单文档原子更新。
        Update update = new Update()
                .set("status", QuestionnaireStatus.PUBLISHED)
                .set("publishedAt", Instant.now())
                .currentDate("updatedAt")
                .inc("version", 1L);
        requireUpdated(mongoTemplate.updateFirst(query, update, QuestionnaireDocument.class),
                id, expectedVersion, QuestionnaireStatus.DRAFT, null);
        return getById(id);
    }

    @Override
    public QuestionnaireResponse close(String id, long expectedVersion) {
        validateExpectedVersion(expectedVersion);
        Query query = mutationQuery(id, expectedVersion, QuestionnaireStatus.PUBLISHED);
        Update update = new Update()
                .set("status", QuestionnaireStatus.CLOSED)
                .set("closedAt", Instant.now())
                .currentDate("updatedAt")
                .inc("version", 1L);
        requireUpdated(mongoTemplate.updateFirst(query, update, QuestionnaireDocument.class),
                id, expectedVersion, QuestionnaireStatus.PUBLISHED, null);
        return getById(id);
    }

    @Override
    public boolean deleteDraft(String id, long expectedVersion) {
        validateExpectedVersion(expectedVersion);
        DeleteResult result = mongoTemplate.remove(
                draftMutationQuery(id, expectedVersion), QuestionnaireDocument.class);
        if (result.getDeletedCount() == 1) {
            return true;
        }
        diagnoseMutationFailure(id, expectedVersion, QuestionnaireStatus.DRAFT, null);
        return false;
    }

    private Query draftMutationQuery(String id, long expectedVersion) {
        return mutationQuery(id, expectedVersion, QuestionnaireStatus.DRAFT);
    }

    private Query mutationQuery(String id, long expectedVersion, QuestionnaireStatus status) {
        validateId(id, "问卷ID");
        return Query.query(Criteria.where("_id").is(id)
                .and("status").is(status)
                .and("version").is(expectedVersion));
    }

    private void requireUpdated(UpdateResult result, String id, long expectedVersion,
                                QuestionnaireStatus requiredStatus, String questionId) {
        if (result.getMatchedCount() == 1) {
            return;
        }
        diagnoseMutationFailure(id, expectedVersion, requiredStatus, questionId);
    }

    private void diagnoseMutationFailure(String id, long expectedVersion,
                                         QuestionnaireStatus requiredStatus, String questionId) {
        QuestionnaireDocument current = requireQuestionnaire(id);
        if (current.getStatus() != requiredStatus) {
            throw new BusinessException("当前操作要求问卷状态为" + requiredStatus
                    + "，实际状态为" + current.getStatus());
        }
        if (current.getVersion() == null || current.getVersion() != expectedVersion) {
            throw new BusinessException("问卷已被其他请求修改，请使用最新version重试");
        }
        if (questionId != null && current.getQuestions().stream()
                .noneMatch(question -> questionId.equals(question.getId()))) {
            throw new BusinessException("题目不存在");
        }
        throw new BusinessException("问卷更新失败");
    }

    private QuestionnaireDocument requireQuestionnaire(String id) {
        validateId(id, "问卷ID");
        return questionnaireRepository.findById(id)
                .orElseThrow(() -> new BusinessException("问卷不存在"));
    }

    private QuestionnaireResponse toResponse(QuestionnaireDocument document, PgUser creator) {
        QuestionnaireResponse result = new QuestionnaireResponse();
        result.setId(document.getId());
        result.setTitle(document.getTitle());
        result.setDescription(document.getDescription());
        result.setStatus(document.getStatus());
        result.setCreatedBy(postgresUserLookup.toSummary(creator));
        result.setQuestions(document.getQuestions() == null ? List.of() : document.getQuestions().stream()
                .map(this::toQuestionResponse)
                .toList());
        result.setVersion(document.getVersion());
        result.setCreatedAt(document.getCreatedAt());
        result.setUpdatedAt(document.getUpdatedAt());
        result.setPublishedAt(document.getPublishedAt());
        result.setClosedAt(document.getClosedAt());
        return result;
    }

    private QuestionResponse toQuestionResponse(QuestionDefinition question) {
        QuestionResponse result = new QuestionResponse();
        result.setId(question.getId());
        result.setTitle(question.getTitle());
        result.setType(question.getType());
        result.setRequired(question.isRequired());
        result.setSettings(question.getSettings() == null
                ? Map.of()
                : new LinkedHashMap<>(question.getSettings()));
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

    private void validatePage(Integer pageNum, Integer pageSize) {
        BusinessAssert.isTrue(pageNum != null && pageNum >= 1, "页码必须大于等于1");
        BusinessAssert.isTrue(pageSize != null && pageSize >= 1 && pageSize <= MAX_PAGE_SIZE,
                "每页数量必须在1到100之间");
    }

    private void validateExpectedVersion(long expectedVersion) {
        BusinessAssert.isTrue(expectedVersion >= 0, "expectedVersion不能为负数");
    }

    private void validateId(String id, String fieldName) {
        BusinessAssert.hasText(id, fieldName + "不能为空");
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
