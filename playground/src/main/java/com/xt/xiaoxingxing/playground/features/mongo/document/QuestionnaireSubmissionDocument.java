package com.xt.xiaoxingxing.playground.features.mongo.document;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 一次完整提交对应一个答卷文档，答案不会无限追加到问卷文档中。 */
@Data
@Document(collection = "questionnaire_submissions")
@CompoundIndexes({
        @CompoundIndex(name = "uk_submission_questionnaire_user",
                def = "{'questionnaireId': 1, 'userId': 1}", unique = true),
        @CompoundIndex(name = "idx_submission_questionnaire_time",
                def = "{'questionnaireId': 1, 'submittedAt': -1}"),
        @CompoundIndex(name = "idx_submission_user_time",
                def = "{'userId': 1, 'submittedAt': -1}")
})
public class QuestionnaireSubmissionDocument {

    @Id
    private String id;

    private String questionnaireId;

    /** 保存问卷标题快照，答卷列表不需要为每条记录再次查询问卷。 */
    private String questionnaireTitle;

    /** playground 本地 users 表的用户 ID。 */
    private Long userId;

    private List<AnswerSnapshot> answers = new ArrayList<>();

    @CreatedDate
    private Instant submittedAt;
}
