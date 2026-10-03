// 端到端验证脚本：跨日计时 / 双方确认冲突 / 退款重复回传 / 角色权限。
// 用法：node scripts/verify.mjs [baseUrl]
const BASE = process.argv[2] || 'http://localhost:8080';

let passed = 0;
let failed = 0;

function ok(name, cond, extra = '') {
  if (cond) {
    passed++;
    console.log(`  ✅ ${name}`);
  } else {
    failed++;
    console.log(`  ❌ ${name} ${extra}`);
  }
}

async function call(method, path, body, expectedStatus = 200) {
  const res = await fetch(BASE + '/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined
  });
  const text = await res.text();
  let data;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (res.status !== expectedStatus) {
    throw new Error(`${method} ${path} 期望 ${expectedStatus} 实际 ${res.status}: ${text}`);
  }
  return data;
}
const get = (p, s = 200) => call('GET', p, null, s);
const post = (p, b, s = 200) => call('POST', p, b, s);

const y = (f) => (f / 100).toFixed(2);

async function main() {
  console.log('\n=== 场景 1：跨日计时 + 中途外出 + 加时（订单 #1）===');
  const o1 = await get('/orders/1?viewerId=1&viewerRole=CUSTOMER');
  ok('标记为跨日且覆盖 2 个自然日', o1.breakdown.crossDay === true && o1.breakdown.dayCount === 2,
     JSON.stringify({ crossDay: o1.breakdown.crossDay, dayCount: o1.breakdown.dayCount }));
  ok('到达 22:00、离开次日 02:40',
     o1.breakdown.arrivalText === '2026-10-08 22:00' &&
     o1.breakdown.departureText === '2026-10-09 02:40',
     `${o1.breakdown.arrivalText} → ${o1.breakdown.departureText}`);
  ok('外出 30 分钟不计入，实际工作 250 分钟', o1.breakdown.workedMinutes === 250,
     `worked=${o1.breakdown.workedMinutes}`);
  ok('识别为加时场景', o1.breakdown.scenario === 'OVERTIME');
  ok('约定 200.00 元 + 加时 25.00 元 = 225.00 元', o1.breakdown.payableFen === 22500,
     `payable=${y(o1.breakdown.payableFen)}`);

  console.log('\n=== 场景 2：迟到扣减（订单 #2）===');
  const o2 = await get('/orders/2?viewerId=2&viewerRole=CUSTOMER');
  ok('迟到 20 分钟', o2.breakdown.lateMinutes === 20, `late=${o2.breakdown.lateMinutes}`);
  ok('宽限 5 分钟后扣 12.50 元，约定 100.00 元应收 87.50 元',
     o2.breakdown.lateDeductionFen === 1250 && o2.breakdown.payableFen === 8750,
     `deduction=${y(o2.breakdown.lateDeductionFen)}, payable=${y(o2.breakdown.payableFen)}`);

  console.log('\n=== 场景 3：双方确认冲突 → 自动挂起（订单 #3）===');
  const o3 = await get('/orders/3?viewerId=6&viewerRole=STAFF');
  ok('订单处于争议挂起', o3.status === 'DISPUTED' && o3.frozen === true, o3.status);
  ok('识别出离开时间相差 60 分钟',
     o3.conflicts.length === 1 && o3.conflicts[0].diffMinutes === 60,
     JSON.stringify(o3.conflicts));
  ok('服务员口径工作 240 分钟（含 60 分钟加时费）',
     o3.workerSideBreakdown.workedMinutes === 240 &&
     o3.workerSideBreakdown.payableFen === 22000,
     `worker=${o3.workerSideBreakdown.workedMinutes}min/${y(o3.workerSideBreakdown.payableFen)}`);
  ok('客户口径工作 180 分钟（无加时，165.00 元）',
     o3.customerSideBreakdown.workedMinutes === 180 &&
     o3.customerSideBreakdown.payableFen === 16500,
     `customer=${o3.customerSideBreakdown.workedMinutes}min/${y(o3.customerSideBreakdown.payableFen)}`);

  console.log('\n--- 挂起期间任何人都不能改计时/结算 ---');
  await post('/orders/3/clock', {
    viewerId: 4, viewerRole: 'WORKER', eventType: 'DEPARTURE'
  }, 409).then(() => ok('服务员重复/改计时被拒绝（409）', true))
    .catch((e) => ok('服务员重复/改计时被拒绝（409）', false, e.message));
  await post('/orders/3/clock', {
    viewerId: 3, viewerRole: 'CUSTOMER', eventType: 'ARRIVAL'
  }, 409).then(() => ok('客户挂起期间改计时被拒绝（409）', true))
    .catch((e) => ok('客户挂起期间改计时被拒绝（409）', false, e.message));
  await post('/orders/3/confirm-completion', {
    viewerId: 3, viewerRole: 'CUSTOMER'
  }, 409).then(() => ok('挂起期间客户不能确认完成（409）', true))
    .catch((e) => ok('挂起期间客户不能确认完成（409）', false, e.message));
  await post('/orders/3/settle', {
    viewerId: 3, viewerRole: 'CUSTOMER', idempotencyKey: 'x'
  }, 409).then(() => ok('挂起期间不能结算（409）', true))
    .catch((e) => ok('挂起期间不能结算（409）', false, e.message));

  console.log('\n--- 客服权限边界 ---');
  await post('/orders/2/confirm-completion', {
    viewerId: 6, viewerRole: 'STAFF'
  }, 403).then(() => ok('客服不能代客户确认完成（403）', true))
    .catch((e) => ok('客服不能代客户确认完成（403）', false, e.message));
  await post('/orders/3/resolve', {
    staffId: 4, staffRole: 'WORKER', resolution: 'DENY'
  }, 403).then(() => ok('服务员不能处理争议（403）', true))
    .catch((e) => ok('服务员不能处理争议（403）', false, e.message));

  console.log('\n--- 客服按客户主张处理争议（DENY，180 分钟）---');
  const resolved = await post('/orders/3/resolve', {
    staffId: 6, staffRole: 'STAFF', resolution: 'DENY',
    claimedBilledMinutes: 180, note: '核对双方记录，采信客户 16:00 离开'
  });
  ok('争议解冻，回到待确认完成', resolved.order.status === 'IN_PROGRESS', resolved.order.status);
  ok('账单按客户主张重算为 165.00 元',
     resolved.order.breakdown.payableFen === 16500,
     y(resolved.order.breakdown.payableFen));

  const confirmed = await post('/orders/3/confirm-completion', {
    viewerId: 3, viewerRole: 'CUSTOMER'
  });
  ok('客户本人确认完成', confirmed.order.status === 'COMPLETED', confirmed.order.status);

  console.log('\n=== 场景 4：结算重复提交不二次扣款（订单 #3）===');
  const s1 = await post('/orders/3/settle', {
    viewerId: 3, viewerRole: 'CUSTOMER', idempotencyKey: 'settle-3'
  });
  ok('首次结算扣款成功（165.00 元）',
     s1.order.status === 'SETTLED' && s1.order.chargedFen === 16500,
     `${s1.order.status}/${y(s1.order.chargedFen)}`);
  const s2 = await post('/orders/3/settle', {
    viewerId: 3, viewerRole: 'CUSTOMER', idempotencyKey: 'settle-3'
  });
  ok('相同请求重复提交：幂等回放、未再扣款',
     s2.idempotentReplay === true && s2.order.chargedFen === 16500 &&
     s2.order.payments.filter((p) => p.type === 'CHARGE').length === 1,
     JSON.stringify({ replay: s2.idempotentReplay, charged: y(s2.order.chargedFen) }));

  console.log('\n=== 场景 5：退款回执重复回传不多退（订单 #4）===');
  const r1 = await post('/orders/4/payments/callback', {
    type: 'REFUND', idempotencyKey: 'refund-demo-4', amountFen: 10000
  });
  ok('首次退款回执入账 100.00 元',
     !r1.idempotentReplay && r1.order.refundedFen === 10000,
     JSON.stringify({ replay: r1.idempotentReplay, refunded: y(r1.order.refundedFen) }));
  const r2 = await post('/orders/4/payments/callback', {
    type: 'REFUND', idempotencyKey: 'refund-demo-4', amountFen: 10000
  });
  ok('相同回执重复回传：幂等拦截、不多退',
     r2.idempotentReplay === true && r2.order.refundedFen === 10000 &&
     r2.order.payments.filter((p) => p.type === 'REFUND').length === 1,
     JSON.stringify({ replay: r2.idempotentReplay, refunded: y(r2.order.refundedFen) }));
  const over = await post('/orders/4/payments/callback', {
    type: 'REFUND', idempotencyKey: 'refund-demo-4-b', amountFen: 1
  }, 409);
  ok('可退余额为 0 时换新键再退被拒绝（409）',
     typeof over === 'object' && over.error && over.error.includes('可退'), JSON.stringify(over));

  console.log('\n=== 权限：服务员只能看自己的订单 ===');
  const w1Orders = await get('/orders?viewerId=4&viewerRole=WORKER');
  ok('赵姐只看到自己的订单（#1/#3/#5），看不到 #2/#4',
     w1Orders.every((o) => o.workerName.includes('赵姐')) && w1Orders.length === 3,
     JSON.stringify(w1Orders.map((o) => o.id)));
  await get('/orders/2?viewerId=4&viewerRole=WORKER', 403)
    .then(() => ok('赵姐查看他人订单 #2 被拒绝（403）', true))
    .catch((e) => ok('赵姐查看他人订单 #2 被拒绝（403）', false, e.message));

  console.log(`\n结果：${passed} 通过，${failed} 失败\n`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error('\n💥 验证脚本异常：', e.message);
  process.exit(2);
});
