# 家政钟点工 · 服务时长与迟到结算系统

客户预约钟点工，双方凭实际服务记录确认费用；迟到、加时、提前结束、中途外出、跨日服务
按规则计费；出现时间分歧、客户拒签或质量异议时先**挂起争议金额**，由客服依据双方记录
裁定，任何一方都不能直接改计时。结算确认与退款回执均做**幂等**处理。

- 后端：Java 21 + Quarkus 3 + Hibernate ORM (Panache) + SQLite
- 前端：Svelte 4 + Vite
- 定位与支付回执：本地模拟（无外部依赖）

## 一条命令启动

```bash
./run.sh
```

脚本会自动：构建 Svelte → `mvn package` 打包 → 启动单进程（同时提供页面与 API）。

打开 <http://localhost:8080>，右上角切换演示身份（2 位客户、2 位钟点工、1 位客服）。

接口级演示（跨日计时 / 双方确认冲突 / 退款回执重复回传）：

```bash
./demo.sh
```

> 环境要求：JDK 21、Maven、Node 18+。SQLite 数据库在 `data/cleaning.db`，
> 每次启动自动重建并播种 4 个演示订单。

## 计费规则

| 情形 | 规则 |
|---|---|
| 约定费用 | 约定分钟数 × 时薪 / 60 |
| 迟到 | 实际到达晚于预约开始的分钟数，**超出宽限（默认 10 分钟）**的部分按单价扣减 |
| 提前结束 | 实际离开早于预约结束，少做的分钟按单价扣回 |
| 中途外出 | OUT→BACK 配对时段**不计费**，按分钟从打包费用扣减 |
| 加时 | 实际离开晚于预约结束的分钟，**客户确认任务完成后**按单价计加时费 |
| 临时加活有异议 | 客服核准后追加（`approvedExtraCents`） |
| 跨日服务（如 22:00→次日 03:00） | 直接以 `LocalDateTime` 起止差计算，无需特殊处理 |
| 争议挂起 | 挂起金额从应付中冻结，订单不可结算、计时不可改动 |

## 关键业务约束

1. **记录不可单方修改**：到达/离开打卡提交后只能由对方确认；否认即构成"确认冲突"，
   自动挂起争议金额并冻结订单。
2. **争议期冻结**：中途外出争议、客户拒签、时间分歧期间，双方新增/修改时间记录、
   结算全部返回 409。
3. **只有客服能裁定**：采纳到达/离开时间（写入 `adoptedAt`）、认定是否完成、
   工时扣减、加活核准、挂起金额释放给钟点工或退客户。
4. **客服不能代客户确认完成**（接口角色校验返回 403）；钟点工只能看到自己的订单。
5. **结算幂等**：`settlements.idempotency_key` 唯一约束 + 订单唯一结算单，
   重复提交返回首条结算结果，不二次扣款。
6. **退款幂等且不多退**：`refunds.payment_callback_id` 唯一约束，同一支付回执重复
   回传只返回首条结果；申请、审核、出款三道环节均按"已退/处理中 ≤ 实付总额"夹紧。

## 预置演示数据

| 订单 | 场景 |
|---|---|
| ORD-CROSS-01 | **跨日** 22:00→次日 03:00：迟到 25 分钟（宽限 10）、外出 20 分钟、加时 30 分钟 |
| ORD-CONFLICT-02 | **双方确认冲突**：钟点工称 09:05 到，客户称 09:40 到，¥25 争议金额挂起中 |
| ORD-REJECT-03 | 提前 40 分钟离开 + **客户拒签**，¥40 挂起中 |
| ORD-REFUND-04 | 已结算 ¥100、退款 ¥30 处理中，用于演示**退款回执重复回传**不多退 |

## 主要接口（`X-Actor-Id` 头切换身份）

| 方法 & 路径 | 角色 | 说明 |
|---|---|---|
| `POST /api/orders` | 客户 | 下单（支持跨日起止时间） |
| `POST /api/orders/{id}/clock` | 本单钟点工 | 到达/离开打卡（模拟定位） |
| `POST /api/orders/{id}/midway` | 双方 | 登记中途外出/返回 |
| `POST /api/orders/{id}/logs/{logId}/confirm` | 对方 | 确认/否认（否认→挂起争议） |
| `POST /api/orders/{id}/complete` | **仅客户** | 确认完成 / 拒签 |
| `POST /api/orders/{id}/disputes` | 双方 | 发起争议并挂起金额 |
| `POST /api/orders/disputes/{id}/resolve` | **仅客服** | 裁定时间/完成/扣减/挂起金额去向 |
| `POST /api/orders/{id}/settle` | 客户 | 结算扣款（幂等键） |
| `POST /api/orders/{id}/refunds` | 客户 | 申请退款（总额上限校验） |
| `POST /api/orders/refunds/{id}/approve` | 客服 | 审核退款（再次夹紧上限） |
| `POST /api/orders/refunds/callback` | 支付渠道 | 退款回执回调（回执号幂等） |

## 目录结构

```
src/main/java/com/example/cleaning/
  model/        枚举（角色、订单/记录/争议状态、记录与争议类型）
  entity/       User Order TimeLog Dispute Settlement Refund
  service/      BillingService（计费规则） OrderService（业务/幂等）
                PaymentGateway（支付模拟） LocationSimulator（定位模拟）
  web/          REST 资源、身份头解析、异常映射、聚合视图 DTO
  bootstrap/    SeedData（启动播种四个演示场景）
frontend/src/
  App.svelte         身份切换、订单列表、下单
  lib/BillPanel      约定时长/实际时长/费用变化对照
  lib/LogsPanel      打卡与双方确认冲突
  lib/DisputePanel   争议挂起与客服裁定
  lib/SettlementPanel 完成确认、幂等结算、退款与重复回执
```
