package com.xt.xiaoxingxing.playground.rocketmq.controller;

import com.xt.xiaoxingxing.playground.rocketmq.dto.ProductResponse;
import com.xt.xiaoxingxing.playground.rocketmq.service.ProductService;
import com.xt.xiaoxingxing.shared.common.Result;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 商品控制器。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/rocketmq/products")
public class ProductController {

    private final ProductService productService;

    /** 查询商品。 */
    @GetMapping("/{productId}")
    public Result<ProductResponse> getProduct(
            @PathVariable @Positive(message = "productId必须大于0") Long productId) {
        return Result.ok(productService.getProduct(productId));
    }
}
