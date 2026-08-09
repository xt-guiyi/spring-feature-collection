package com.xt.xiaoxingxing.playground.mongo.document;

import com.xt.xiaoxingxing.playground.mongo.enums.QuestionnaireStatus;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 问卷聚合根：题目随问卷一起读取和修改，因此以内嵌数组保存。 */
@Data
@Document(collection = "questionnaires")
@CompoundIndexes({
        @CompoundIndex(name = "idx_questionnaire_status_updated",
                def = "{'status': 1, 'updatedAt': -1}"),
        @CompoundIndex(name = "idx_questionnaire_creator_created",
                def = "{'createdByUserId': 1, 'createdAt': -1}")
})
public class QuestionnaireDocument {

    @Id
    private String id;

    private String title;

    private String description;

    private QuestionnaireStatus status;

    /** PostgreSQL playground.users.id，只保存标量ID，不使用 MongoDB DBRef。 */
    private Long createdByUserId;

    private List<QuestionDefinition> questions = new ArrayList<>();

    /** 用于草稿并发编辑；每次条件更新都要求客户端提交当前版本。 */
    @Version
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant publishedAt;

    private Instant closedAt;
}
