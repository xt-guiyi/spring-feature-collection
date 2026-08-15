package com.xt.xiaoxingxing.playground.rocketmq.product;

import com.xt.xiaoxingxing.shared.common.Result;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品库存 Cache Aside 查询入口。
 *
 * <p>连续查询同一商品可观察数据库回填和 Redis 命中；订单扣库存或取消恢复库存后，消息消费者会删除相同键，
 * 下一次查询重新从 PostgreSQL 获取权威库存。</p>
 */
@Validated
@RestController("productController")
@RequiredArgsConstructor
@RequestMapping("/api/playground/rocketmq/products")
public class ProductController {

    private final ProductService productService;

    /** Redis 故障时降级 PostgreSQL；商品不存在或数据库失败则返回正常业务错误。 */
    @GetMapping("/{productId}")
    public Result<ProductResponse> getProduct(
            @PathVariable @Positive(message = "productId必须大于0") Long productId) {
        return Result.ok(productService.getProduct(productId));
    }
}
