<script>
  import { api } from '../api.js';
  import { toast } from './toast.js';
  import { yuan } from './format.js';

  export let order;
  export let refresh;

  let settleKey = '';
  let settleBusy = false;
  let settleCount = 0;
  let refundYuan = '';
  let refundReason = '';

  async function confirmComplete(accepted) {
    try {
      await api.complete(order.id, { accept: accepted });
      toast(accepted ? '已确认任务完成，加时费已计入' : '已拒签，争议金额自动挂起交客服处理', accepted ? 'ok' : 'info');
      await refresh();
    } catch (e) { toast(e.message, 'err'); }
  }

  // 结算确认：可连续点击模拟重复提交，后端靠幂等键只扣一次款
  async function settle() {
    if (settleBusy) return;
    settleBusy = true;
    settleCount++;
    const key = settleKey || `settle-${order.orderNo}`;
    try {
      const v = await api.settle(order.id, key);
      if (v.settlement?.idempotentReplay)
        toast(`第 ${settleCount} 次提交为重复请求，命中幂等，未二次扣款`, 'info');
      else
        toast(`扣款成功 ${yuan(v.settlement.totalCents)}，流水 ${v.settlement.paymentTxnNo}`, 'ok');
      await refresh();
    } catch (e) { toast(e.message, 'err'); }
    finally { settleBusy = false; }
  }

  async function requestRefund() {
    try {
      const cents = Math.round(Number(refundYuan) * 100);
      const r = await api.refund(order.id, { cents, reason: refundReason });
      toast(`退款申请已提交：${yuan(r.refundCents)}，等待客服审核`, 'ok');
      refundYuan = ''; refundReason = '';
      await refresh();
    } catch (e) { toast(e.message, 'err'); }
  }

  async function approve(r) {
    try {
      const res = await api.approveRefund(r.id);
      toast(`客服已审核，退款进入处理中，回执号 ${res.paymentCallbackId}`, 'info');
      await refresh();
    } catch (e) { toast(e.message, 'err'); }
  }

  // 支付回执回调：同一回执号可多次"回传"，验证不多退
  async function callback(r, nth) {
    try {
      const res = await api.refundCallback({ paymentCallbackId: r.paymentCallbackId, result: 'SUCCESS' });
      toast(`第 ${nth} 次回执回传：状态 ${res.status}，退款流水 ${res.refundTxnNo || '—'}（幂等，不重复出款）`, 'info');
      await refresh();
    } catch (e) { toast(e.message, 'err'); }
  }
</script>

<div class="card">
  <h2>结算、支付回执与退款</h2>

  {#if !order.customerConfirmedComplete && order.perms.canConfirmComplete}
    <div class="muted small">钟点工已离开。请客户确认任务完成情况（客服不能代客户确认）：</div>
    <div class="row mt8">
      <button class="small success" on:click={() => confirmComplete(true)}>确认任务完成</button>
      <button class="small danger" on:click={() => confirmComplete(false)}>拒签（挂起争议）</button>
    </div>
  {/if}

  {#if order.settlement}
    <div style="background:var(--green-bg);border:1px solid #bbf7d0;border-radius:8px;padding:10px 13px;">
      <div class="row">
        <strong>已结算</strong>
        <span class="muted small">幂等键 {order.settlement.idempotencyKey}</span>
        <span class="spacer"></span>
        <strong>{yuan(order.settlement.totalCents)}</strong>
      </div>
      <div class="kv small mt8">
        <span class="k">支付流水</span><span>{order.settlement.paymentTxnNo}</span>
        <span class="k">扣款时间</span><span>{order.settlement.paidAt}</span>
        <span class="k">实际扣款</span><span>{yuan(order.settlement.paidCents ?? order.settlement.totalCents)}</span>
      </div>
      <div class="muted small mt8">结算单已存在；重复提交结算只会返回本单，不会二次扣款。</div>
    </div>
  {:else if order.perms.canSettle}
    <div class="mt8">
      <label class="field">结算幂等键（留空使用默认键，重复点击可验证不重复扣款）
        <input bind:value={settleKey} placeholder="settle-{order.orderNo}" />
      </label>
      <div class="row mt8">
        <button on:click={settle} disabled={settleBusy}>确认结算并扣款 {yuan(order.bill.payableCents)}</button>
        <span class="muted small">已提交 {settleCount} 次（可连点测试幂等）</span>
      </div>
    </div>
  {:else if order.perms.frozenByDispute}
    <div class="freeze-banner mt8">争议挂起期间不可结算，客服裁定后自动解冻。</div>
  {/if}

  {#if order.refunds.length > 0}
    <h3>退款记录（支付回执本地模拟）</h3>
    <ul class="logs">
      {#each order.refunds as r (r.id)}
        <li>
          <div class="log-head">
            <span class="badge log-{r.status === 'PAID' ? 'CONFIRMED' : r.status === 'FAILED' ? 'REJECTED' : 'PENDING'}">{r.statusLabel}</span>
            <span class="spacer"></span>
            <strong>申请 {yuan(r.requestCents)}</strong>
            <span>实退 {yuan(r.refundCents)}</span>
          </div>
          <div class="log-body">
            {#if r.reason}<span>{r.reason}</span>{/if}
            {#if r.paymentCallbackId}<span>支付回执号：<code>{r.paymentCallbackId}</code></span>{/if}
            {#if r.refundTxnNo}<span>退款流水：{r.refundTxnNo} · {r.processedAt}</span>{/if}
          </div>
          <div class="row mt8">
            {#if r.status === 'PENDING' && order.perms.canResolveDisputes}
              <button class="small" on:click={() => approve(r)}>客服审核通过</button>
            {/if}
            {#if r.status === 'PROCESSING'}
              <button class="small ghost" on:click={() => callback(r, 1)}>模拟支付回执回传（第 1 次）</button>
              <button class="small subtle" on:click={() => callback(r, 2)}>同一回执重复回传（第 2 次）</button>
              <button class="small subtle" on:click={() => callback(r, 3)}>同一回执重复回传（第 3 次）</button>
            {/if}
            {#if r.status === 'PAID'}
              <button class="small subtle" on:click={() => callback(r, 99)}>重复回传已到账回执（应幂等）</button>
            {/if}
          </div>
        </li>
      {/each}
    </ul>
  {/if}

  {#if order.perms.canRequestRefund}
    <h3>申请售后退款</h3>
    <div class="row">
      <label class="field">退款金额（元）<input type="number" min="0.01" step="0.01" bind:value={refundYuan} /></label>
      <label class="field" style="flex:1;">退款原因<input bind:value={refundReason} placeholder="如：卫生间清洁未达标" /></label>
      <button class="small ghost" on:click={requestRefund}>提交退款申请</button>
    </div>
    <div class="muted small mt8">退款总额不得超过实付金额；客服审核后由支付渠道回执触发出款，回执重复回传不会多退。</div>
  {/if}
</div>
