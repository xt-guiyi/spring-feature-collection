# Drools 订单规则学习模块

本模块使用内嵌式 Drools，规则文件固定放在项目的 classpath 中，不连接数据库，也不实现动态热加载。

## 执行流程

```text
HTTP 请求
    ↓
OrderRuleFact（订单事实）
    ↓ insert
KieSession
    ↓ fireAllRules
订单规则文件
    ↓
规则计算结果
```

- `OrderRuleFact`：交给 Drools 匹配的事实对象。
- `KieContainer`：加载并持有编译后的规则库。
- `KieSession`：保存本次请求的事实并执行规则；服务每次请求创建并释放一个 Session。
- `insert`：把订单事实放入规则引擎。
- `fireAllRules`：让 Drools 匹配并执行当前事实可以触发的全部规则。
- `salience`：规则优先级，数值越大越先执行。

## 规则文件

规则位于：

```text
src/main/resources/rules/order/order-rules.drl
```

当前包含：

| 规则 | 条件 | 结果 |
|---|---|---|
| `VIP_HIGH_VALUE_DISCOUNT` | VIP 且金额不少于 500 | 九折 |
| `NEW_USER_DISCOUNT` | 新用户且不是 VIP | 九五折 |
| `LARGE_ORDER_FREE_SHIPPING` | 金额不少于 500 | 免运费 |
| `CALCULATE_FINAL_AMOUNT` | 最终金额尚未计算 | 计算折扣后金额加运费 |

规则名称会通过 `appliedRules` 返回，便于观察本次请求实际触发了哪些规则。

## 调用接口

```http
POST /api/playground/drools/orders/evaluate
Content-Type: application/json
```

```json
{
  "orderNo": "DROOLS-001",
  "totalAmount": 800,
  "vip": true,
  "newUser": false
}
```

也可以直接使用 curl：

```bash
curl -X POST "http://localhost:4379/api/playground/drools/orders/evaluate" \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "DROOLS-001",
    "totalAmount": 800,
    "vip": true,
    "newUser": false
  }'
```

Swagger 地址：

```text
http://localhost:4379/swagger-ui.html
```

规则文件是随应用打包的。修改 `.drl` 后重新启动应用即可加载新规则；本模块暂不实现运行时动态刷新。
