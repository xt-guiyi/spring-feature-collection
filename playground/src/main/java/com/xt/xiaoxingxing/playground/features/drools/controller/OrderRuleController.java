package com.xt.xiaoxingxing.playground.features.drools.controller;

import com.xt.xiaoxingxing.playground.features.drools.dto.request.OrderRuleEvaluateRequest;
import com.xt.xiaoxingxing.playground.features.drools.dto.response.OrderRuleEvaluateResponse;
import com.xt.xiaoxingxing.playground.features.drools.service.OrderRuleService;
import com.xt.xiaoxingxing.shared.core.response.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Drools 订单规则学习接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/drools/orders")
public class OrderRuleController {

    private final OrderRuleService orderRuleService;

    /** 执行订单规则。 */
    @PostMapping("/evaluate")
    public Result<OrderRuleEvaluateResponse> evaluate(
            @Valid @RequestBody OrderRuleEvaluateRequest request) {
        return Result.ok(orderRuleService.evaluate(request));
    }
}
