#!/usr/bin/env bash
# 接口级演示脚本：跨日计时 / 双方确认冲突（冻结+客服裁定）/ 退款回执重复回传
# 依赖 run.sh 已启动服务（默认 http://localhost:8080）
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
C='-s -H Content-Type:application/json'

# 种子账户：1=客户王芳 2=客户李强 3=钟点工张阿姨 4=钟点工陈师傅 5=客服小周
say() { printf '\n\033[1;36m%s\033[0m\n' "$*"; }
j() { python3 -c "import json,sys;d=json.load(sys.stdin);$1"; }

say "场景一：跨日服务计时（订单 ORD-CROSS-01，22:00 → 次日 03:00）"
curl -s -H "X-Actor-Id: 1" "$BASE/api/orders/1" | j "
b=d['bill']
print('  约定时段:', d['scheduledStart'], '→', d['scheduledEnd'], '（跨日', b['scheduledMinutes'], '分钟）')
print('  实际:', b['arriveAt'], '→', b['leaveAt'], '，在岗', b['actualMinutes'], '分钟，外出', b['outMinutes'], '分钟')
[print('  费用项:', i['label'], '=', i['cents']) for i in b['items']]
print('  当前应付: ¥%.2f' % (b['payableCents']/100))
"

say "场景二：双方确认冲突（订单 ORD-CONFLICT-02：钟点工称 09:05 到，客户称 09:40）"
echo "  2.1 争议挂起期间，钟点工想补离开打卡（应被拦截，任何一方不能改计时）："
curl -s $C -X POST -H "X-Actor-Id: 4" -d '{"type":"LEAVE","at":"'$(date +%F)'T12:00:00"}' "$BASE/api/orders/2/clock" \
  | j "print('   接口返回:', d.get('error') or d)"
echo "  2.2 客服裁定：采纳 09:35 到达；挂起 ¥25 中 ¥5 付钟点工、¥20 退客户："
curl -s $C -X POST -H "X-Actor-Id: 5" \
  -d '{"adoptedArriveAt":"'$(date +%F)'T09:35:00","serviceAccepted":true,"releaseToCleanerCents":500,"refundToCustomerCents":2000,"resolution":"门禁记录显示09:35进门"}' \
  "$BASE/api/orders/disputes/1/resolve" | j "print('   裁定提交:', d.get('error') or d['status'])"
curl -s -H "X-Actor-Id: 5" "$BASE/api/orders/2" | j "
b=d['bill']
print('   解冻后状态:', d['statusLabel'], '，迟到', b['lateMinutes'], '分钟')
[print('   费用项:', i['label'], '=', i['cents']) for i in b['items']]
print('   当前应付: ¥%.2f' % (b['payableCents']/100))
"

say "场景三：退款回执重复回传（订单 ORD-REFUND-04，已付 ¥100，退款 ¥30 处理中）"
for n in 1 2 3; do
  curl -s $C -X POST -d '{"paymentCallbackId":"CB-ORD-REFUND-04-1","result":"SUCCESS"}' \
    "$BASE/api/orders/refunds/callback" | j "print('  回执第 $n 次回传: status=%s 实退=¥%.2f 流水=%s' % (d['status'], d['refundCents']/100, d['refundTxnNo']))"
done
echo "  三次同一回执号 → 同一退款流水、只出款一次（不多退）。"

say "附加验证：结算重复提交不二次扣款（跨日订单，客户王芳）"
for n in 1 2; do
  curl -s $C -X POST -H "X-Actor-Id: 1" \
    -d '{"idempotencyKey":"demo-settle-cross"}' "$BASE/api/orders/1/settle" \
    | j "s=d['settlement'];print('  第 $n 次提交: 扣款=¥%.2f 幂等重放=%s 流水=%s' % (s['totalCents']/100, s['idempotentReplay'], s['paymentTxnNo']))"
done
echo ""
echo "完成。打开 $BASE 可在页面右上角切换客户/钟点工/客服身份操作。"
