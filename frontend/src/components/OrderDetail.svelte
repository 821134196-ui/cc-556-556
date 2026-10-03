<script>
  import { onMount } from 'svelte';
  import { createEventDispatcher } from 'svelte';
  import { api } from '../api.js';
  import { yuan, toInputValue, toEpoch, ROLE_TEXT, STATUS_BADGE } from '../lib/format.js';
  import Breakdown from './Breakdown.svelte';
  import Timeline from './Timeline.svelte';

  export let selectedId;
  export let currentUser;
  const dispatch = createEventDispatcher();
  const notify = (msg, err, replay) => dispatch('toast', { msg, err, replay });

  let order = null;
  let busy = false;
  let clockTime = '';
  let clockNote = '';
  let disputeReason = '';
  let claimedMinutes = '';
  let resolveNote = '';
  let resolveChoice = 'DENY';
  let resolveClaimed = '';
  let refundKey = '';
  let refundAmount = '';

  onMount(load);
  $: if (selectedId) { clockTime = ''; clockNote = ''; }

  async function load() {
    order = await api.get(
      `/orders/${selectedId}?viewerId=${currentUser.id}&viewerRole=${currentUser.role}`
    );
  }

  async function act(path, body) {
    if (busy) return;
    busy = true;
    try {
      const res = await api.post(`/orders/${selectedId}${path}`, body);
      order = res.order || order;
      notify(res.message, false, res.idempotentReplay);
      dispatch('changed');
    } catch (e) {
      notify(e.message, true);
    } finally {
      busy = false;
    }
  }

  const isWorker = () => currentUser.role === 'WORKER' && order.workerId === currentUser.id;
  const isCustomer = () => currentUser.role === 'CUSTOMER' && order.customerId === currentUser.id;
  const isStaff = () => currentUser.role === 'STAFF';

  const hasEvent = (type, role, action) =>
    order.events.some(
      (e) => e.eventType === type && e.actorRole === role && e.action === action
    );

  const workerArrived = () => hasEvent('ARRIVAL', 'WORKER', 'REPORTED');
  const customerArrived = () => hasEvent('ARRIVAL', 'CUSTOMER', 'CONFIRMED');
  const workerLeft = () => hasEvent('DEPARTURE', 'WORKER', 'REPORTED');
  const customerLeft = () => hasEvent('DEPARTURE', 'CUSTOMER', 'CONFIRMED');
  const outOpen = () => {
    const outs = order.events.filter((e) => e.eventType === 'PAUSE_OUT').length;
    const backs = order.events.filter((e) => e.eventType === 'RETURN').length;
    return outs > backs;
  };

  async function clock(eventType) {
    const epoch = clockTime ? toEpoch(clockTime) : null;
    const location =
      eventType === 'ARRIVAL'
        ? `${order.customerName}家地址附近（模拟定位）`
        : eventType === 'DEPARTURE'
          ? '离开服务地址（模拟定位）'
          : eventType === 'PAUSE_OUT'
            ? '离开服务地址（模拟定位）'
            : '返回服务地址（模拟定位）';
    await act('/clock', {
      viewerId: currentUser.id,
      viewerRole: currentUser.role,
      eventType,
      clockAt: epoch,
      location,
      note: clockNote || null
    });
    clockNote = '';
  }

  async function raiseDispute() {
    await act('/disputes', {
      viewerId: currentUser.id,
      viewerRole: currentUser.role,
      reason: disputeReason || '对完成情况有异议',
      claimedBilledMinutes: claimedMinutes ? Number(claimedMinutes) : null
    });
    disputeReason = '';
    claimedMinutes = '';
  }

  async function settle() {
    // 固定幂等键：重复点“确认结算”只会命中同一笔扣款
    await act('/settle', {
      viewerId: currentUser.id,
      viewerRole: currentUser.role,
      idempotencyKey: `settle-${selectedId}`
    });
  }

  async function refundCallback() {
    await act('/payments/callback', {
      type: 'REFUND',
      idempotencyKey: refundKey || `refund-${selectedId}`,
      amountFen: refundAmount ? Math.round(Number(refundAmount) * 100) : null
    });
  }
</script>

{#if order}
  <button class="back-btn" on:click={() => dispatch('back')}>← 返回订单列表</button>

  <div class="topbar">
    <div>
      <h1>
        订单 #{order.id} · {order.tasks}
        <span class="badge {STATUS_BADGE[order.status] || 'booked'}" style="margin-left:10px">
          {order.statusText}
        </span>
      </h1>
      <div class="sub">
        客户 {order.customerName} · 服务员 {order.workerName} · 预约 {order.scheduledStart}
        （{order.scheduledMinutes} 分钟） · 单价 {yuan(order.hourlyRateFen)} 元/小时
      </div>
    </div>
  </div>

  {#if order.frozen}
    <div class="freeze-banner">
      ⛔ 争议挂起中：{order.disputeReason || '双方记录存在分歧'}。
      计时与结算已冻结，任何一方都不能改计时，等待客服依据双方记录处理。
    </div>
  {/if}

  {#if order.conflicts.length > 0}
    <div class="conflict-box">
      <b>⚠️ 双方确认冲突</b>
      {#each order.conflicts as c}
        <div class="sides">
          <div class="side">
            <div class="muted">服务员记录的{c.point}时间</div>
            <div class="t">{c.workerClockText}</div>
          </div>
          <div class="side">
            <div class="muted">客户记录的{c.point}时间</div>
            <div class="t">{c.customerClockText}</div>
          </div>
          <div class="side" style="flex:0 0 auto;min-width:160px">
            <div class="muted">相差</div>
            <div class="t" style="color:var(--red)">{c.diffMinutes} 分钟</div>
          </div>
        </div>
      {/each}
    </div>
  {/if}

  <!-- 演示快捷入口 -->
  <div class="demo-bar">
    <span class="label">🧪 演示场景：</span>
    <span class="muted">① 跨日计时看 #1　② 确认冲突看 #3　③ 退款重复回传：在下方用相同回执键连点两次</span>
  </div>

  <div class="grid-2">
    <div>
      <Breakdown breakdown={order.breakdown} />

      {#if order.workerSideBreakdown && order.status === 'DISPUTED'}
        <Breakdown breakdown={order.workerSideBreakdown} title="服务员口径（客服参考）" compact />
        <Breakdown breakdown={order.customerSideBreakdown} title="客户口径（客服参考）" compact />
      {/if}

      <div class="panel">
        <h3>支付与退款流水</h3>
        {#if order.payments.length === 0}
          <div class="muted">暂无流水</div>
        {/if}
        {#each order.payments as p}
          <div class="pay-row">
            <span>
              {p.type === 'CHARGE' ? '💳 扣款' : '↩️ 退款'}
              <span class="tag-role">{p.status}</span>
            </span>
            <span class="mono">{p.receiptNo || '—'}</span>
            <span><b>{yuan(p.amountFen)} 元</b></span>
            <span class="muted mono">{p.createdAtText}</span>
          </div>
          <div class="muted mono" style="font-size:11.5px;padding-bottom:6px">
            幂等键：{p.idempotencyKey} · {p.note}
          </div>
        {/each}
        <div style="margin-top:8px;display:flex;gap:16px;font-size:13px">
          <span>累计扣款：{yuan(order.chargedFen)} 元</span>
          <span>累计退款：{yuan(order.refundedFen)} 元</span>
          <span><b>净收：{yuan(order.netPaidFen)} 元</b></span>
        </div>

        {#if order.chargedFen - order.refundedFen > 0}
          <h4>退款回执回传（模拟网关回调，相同回执键重复回传不会多退）</h4>
          <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
            <input placeholder="回执幂等键" bind:value={refundKey}
              style="width:200px" />
            <input placeholder="退款金额（元，留空=全额）" bind:value={refundAmount}
              type="number" style="width:210px" />
            <button class="warn" on:click={refundCallback} disabled={busy}>回传退款回执</button>
          </div>
        {/if}
      </div>
    </div>

    <div>
      <div class="panel">
        <h3>双方服务记录（不可修改，仅可追加）</h3>
        <Timeline events={order.events} />
        {#if order.events.length === 0}
          <div class="muted">还没有任何记录</div>
        {/if}

        <!-- 服务员操作 -->
        {#if isWorker() && !order.frozen && order.status !== 'SETTLED'}
          <h4>服务员打卡（到达/离开需客户另行确认）</h4>
          <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
            <input type="datetime-local" bind:value={clockTime} />
            <input placeholder="备注（选填，如堵车/加活）" bind:value={clockNote} style="flex:1;min-width:160px" />
          </div>
          <div class="actions">
            {#if !workerArrived()}
              <button class="primary" on:click={() => clock('ARRIVAL')} disabled={busy}>上报到达</button>
            {/if}
            {#if workerArrived() && !workerLeft()}
              {#if !outOpen()}
                <button class="warn" on:click={() => clock('PAUSE_OUT')} disabled={busy}>中途外出（暂停计时）</button>
              {:else}
                <button class="primary" on:click={() => clock('RETURN')} disabled={busy}>已返回（恢复计时）</button>
              {/if}
              <button on:click={() => clock('DEPARTURE')} disabled={busy}>上报离开</button>
            {/if}
          </div>
        {/if}

        <!-- 客户操作 -->
        {#if isCustomer() && !order.frozen && order.status !== 'SETTLED'}
          <h4>客户确认记录</h4>
          <div style="display:flex;gap:8px;flex-wrap:wrap;align-items:center">
            <input type="datetime-local" bind:value={clockTime} />
            <input placeholder="备注（选填）" bind:value={clockNote} style="flex:1;min-width:160px" />
          </div>
          <div class="actions">
            {#if !customerArrived()}
              <button class="primary" on:click={() => clock('ARRIVAL')} disabled={busy || !workerArrived()}>
                确认到达时间
              </button>
            {/if}
            {#if customerArrived() && !customerLeft()}
              <button on:click={() => clock('DEPARTURE')} disabled={busy || !workerLeft()}>
                确认离开时间
              </button>
            {/if}
          </div>
          {#if !workerArrived() || !workerLeft()}
            <div class="muted" style="font-size:12px">服务员先上报后，客户才能确认对应时间。</div>
          {/if}

          {#if customerLeft()}
            <h4>完成与结算（只有客户本人能操作）</h4>
            <div class="actions">
              {#if order.status !== 'COMPLETED'}
                <button class="primary" on:click={() => act('/confirm-completion', {
                  viewerId: currentUser.id, viewerRole: 'CUSTOMER'
                })} disabled={busy}>确认任务完成</button>
              {/if}
              {#if order.status === 'COMPLETED'}
                <button class="primary" on:click={settle} disabled={busy}>
                  确认结算扣款 {yuan(order.breakdown.payableFen)} 元
                </button>
              {/if}
            </div>
          {/if}

          <h4>对完成情况有异议 / 拒签</h4>
          <textarea rows="2" bind:value={disputeReason} style="width:100%"
            placeholder="说明争议，如：油烟机未清干净，只认可 90 分钟工作量"></textarea>
          <div class="actions">
            <input type="number" placeholder="主张计费分钟（选填）" bind:value={claimedMinutes}
              style="width:200px" />
            <button class="danger" on:click={raiseDispute} disabled={busy || order.status === 'COMPLETED'}>
              发起争议（挂起金额）
            </button>
          </div>
        {/if}

        {#if isStaff() && order.status === 'DISPUTED'}
          <h4>客服处理争议（只能采信双方记录，不能改计时、不能代确认完成）</h4>
          <label style="display:flex;gap:6px;align-items:center">
            <input type="radio" bind:group={resolveChoice} value="APPROVED" />
            采信服务员记录（按服务员打卡时间计费）
          </label>
          <label style="display:flex;gap:6px;align-items:center;margin-top:4px">
            <input type="radio" bind:group={resolveChoice} value="DENY" />
            采信客户主张
          </label>
          <div class="actions">
            <input type="number" placeholder="客户主张计费分钟（DENY 时选填）"
              bind:value={resolveClaimed} style="width:240px" />
          </div>
          <textarea rows="2" bind:value={resolveNote} style="width:100%;margin-top:6px"
            placeholder="处理依据，如：核对照片与双方定位记录"></textarea>
          <div class="actions">
            <button class="primary" on:click={() => act('/resolve', {
              staffId: currentUser.id,
              staffRole: 'STAFF',
              resolution: resolveChoice,
              note: resolveNote || null,
              claimedBilledMinutes: resolveClaimed ? Number(resolveClaimed) : null
            })} disabled={busy}>提交处理结果并解冻</button>
          </div>
        {/if}

        {#if isStaff() && order.status !== 'DISPUTED'}
          <div class="muted" style="margin-top:10px;font-size:12.5px">
            客服仅查看记录与处理争议；任务完成必须由客户本人确认，客服不能代操作。
          </div>
        {/if}
      </div>

      <div class="panel">
        <h3>争议记录</h3>
        {#if order.disputes.length === 0}
          <div class="muted">无争议</div>
        {/if}
        {#each order.disputes as d}
          <div style="padding:8px 0;border-bottom:1px dashed var(--line)">
            <div>
              <b>{d.status === 'OPEN' ? '⏳ 挂起中' : '✅ 已处理'}</b>
              <span class="muted" style="margin-left:8px">{d.raisedAtText} · {ROLE_TEXT[d.raisedByRole] || d.raisedByRole}（{d.raisedByName}）</span>
            </div>
            <div style="margin:4px 0">{d.reason}</div>
            {#if d.claimedBilledMinutes}
              <div class="muted">主张计费分钟：{d.claimedBilledMinutes} 分钟</div>
            {/if}
            {#if d.status === 'RESOLVED'}
              <div class="muted">
                处理：{d.resolution === 'APPROVED' ? '采信服务员' : '采信客户主张'}
                · {d.resolvedByName} · {d.resolvedAtText}
              </div>
              {#if d.resolutionNote}<div class="muted">依据：{d.resolutionNote}</div>{/if}
            {/if}
          </div>
        {/each}
        {#if order.finalizedAtText}
          <div class="muted" style="margin-top:8px;font-size:12.5px">
            客户确认完成时间：{order.finalizedAtText}（{order.finalizerName}）
          </div>
        {/if}
      </div>
    </div>
  </div>
{:else}
  <div class="muted" style="padding:40px">加载中…</div>
{/if}
