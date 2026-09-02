package com.xt.xiaoxingxing.playground.features.drools.service;

import com.xt.xiaoxingxing.playground.features.drools.fact.OrderRuleFact;
import com.xt.xiaoxingxing.playground.features.drools.dto.request.OrderRuleEvaluateRequest;
import com.xt.xiaoxingxing.playground.features.drools.dto.response.OrderRuleEvaluateResponse;
import com.xt.xiaoxingxing.shared.core.validation.BusinessAssert;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 订单规则执行服务。 */
@Service
public class OrderRuleService {

    private static final String ORDER_RULE_SESSION = "orderRuleSession";

    private final KieContainer kieContainer;

    public OrderRuleService(KieContainer kieContainer) {
        this.kieContainer = kieContainer;
    }

    /** 将订单作为 Fact 放入 Drools，执行订单规则并返回结果。 */
    public OrderRuleEvaluateResponse evaluate(OrderRuleEvaluateRequest request) {
        OrderRuleFact fact = new OrderRuleFact(
                request.getOrderNo(),
                request.getTotalAmount(),
                request.isVip(),
                request.isNewUser());
        List<String> appliedRules = new ArrayList<>();

        KieSession kieSession = BusinessAssert.notNull(
                kieContainer.newKieSession(ORDER_RULE_SESSION),
                "Drools会话orderRuleSession未注册，请检查kmodule.xml");
        try {
            kieSession.setGlobal("appliedRules", appliedRules);
            kieSession.insert(fact);
            kieSession.fireAllRules();
            return new OrderRuleEvaluateResponse(
                    fact.getOrderNo(), fact.getOriginalAmount(), fact.isVip(), fact.isNewUser(),
                    fact.getDiscountRate(), fact.getShippingFee(), fact.getFinalAmount(),
                    List.copyOf(appliedRules));
        } finally {
            kieSession.dispose();
        }
    }
}
