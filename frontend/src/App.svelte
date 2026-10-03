<script>
  import { onMount } from 'svelte';
  import { api, getActorId, setActorId } from './api.js';
  import { toast } from './lib/toast.js';
  import { yuan } from './lib/format.js';
  import BillPanel from './lib/BillPanel.svelte';
  import LogsPanel from './lib/LogsPanel.svelte';
  import DisputePanel from './lib/DisputePanel.svelte';
  import SettlementPanel from './lib/SettlementPanel.svelte';

  let users = [];
  let actorId = getActorId();
  let actor = null;
  let orders = [];
  let selectedId = null;
  let order = null;
  let loading = false;

  // 新建订单
  let showCreate = false;
  $: cleaners = users.filter((u) => u.role === 'CLEANER');
  let f = { cleanerName: '', workContent: '', start: '', end: '', rateCents: 5000, grace: 10 };

  onMount(loadUsers);

  async function loadUsers() {
    users = await api.users();
    if (!actorId) { actorId = users[0]?.id ?? null; setActorId(actorId); }
    await switchActor(actorId);
  }

  async function switchActor(id) {
    actorId = Number(id);
    setActorId(actorId);
    actor = users.find((u) => u.id === actorId) || null;
    selectedId = null;
    order = null;
    await loadOrders();
  }

  async function loadOrders() {
    loading = true;
    try {
      orders = await api.orders();
      if (selectedId) await select(selectedId);
    } catch (e) { toast(e.message, 'err'); }
    loading = false;
  }

  async function select(id) {
    selectedId = id;
    order = await api.order(id);
  }

  async function refreshSelected() {
    await loadOrders();
  }

  function defaultTimes() {
    const d = new Date();
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    const base = d.toISOString().slice(0, 11);
    f.start = base + '09:00';
    f.end = base + '12:00';
  }

  async function createOrder() {
    try {
      const res = await api.createOrder({
        cleanerName: f.cleanerName,
        workContent: f.workContent,
        scheduledStart: f.start,
        scheduledEnd: f.end,
        rateCents: Math.round(Number(f.rateCents)),
        lateGraceMinutes: Number(f.grace)
      });
      toast(`订单 ${res.orderNo} 已创建`, 'ok');
      showCreate = false;
      f = { cleanerName: '', workContent: '', start: '', end: '', rateCents: 5000, grace: 10 };
      await loadOrders();
      await select(res.id);
    } catch (e) { toast(e.message, 'err'); }
  }
</script>

<header class="topbar">
  <div>
    <h1>家政钟点工 · 时长与迟到结算系统</h1>
    <div class="sub">约定时长 / 实际时长 / 费用变化同屏对照 · 争议挂起 · 幂等结算与退款</div>
  </div>
  <span class="spacer"></span>
  <label class="field" style="flex-direction:row;align-items:center;gap:8px;">
    <span class="muted">当前身份（模拟登录）</span>
    <select value={actorId ?? ''} on:change={(e) => switchActor(e.target.value)}>
      {#each users as u}<option value={u.id}>{u.name} · {u.roleLabel}</option>{/each}
    </select>
  </label>
</header>

<div class="layout">
  <aside class="order-list">
    <div class="card" style="padding:10px 14px;">
      <div class="row">
        <strong>订单</strong>
        <span class="spacer"></span>
        {#if actor?.role === 'CUSTOMER'}
          <button class="small ghost" on:click={() => { showCreate = !showCreate; if (showCreate) defaultTimes(); }}>
            {showCreate ? '收起' : '+ 新建预约'}
          </button>
        {/if}
      </div>
    </div>

    {#if showCreate && actor?.role === 'CUSTOMER'}
      <div class="card">
        <h2>新建预约</h2>
        <div style="display:flex;flex-direction:column;gap:9px;">
          <label class="field">钟点工
            <select bind:value={f.cleanerName}>
              <option value="">请选择</option>
              {#each cleaners as c}<option value={c.name}>{c.name}</option>{/each}
            </select>
          </label>
          <label class="field">工作内容<input bind:value={f.workContent} placeholder="如：日常保洁 3 小时" /></label>
          <label class="field">预约开始（可跨日）<input type="datetime-local" bind:value={f.start} /></label>
          <label class="field">预约结束（可在次日）<input type="datetime-local" bind:value={f.end} /></label>
          <div class="row">
            <label class="field" style="flex:1;">时薪（元）<input type="number" min="1" step="0.5" bind:value={f.rateCents} /></label>
            <label class="field" style="flex:1;">迟到宽限（分钟）<input type="number" min="0" bind:value={f.grace} /></label>
          </div>
          <button class="small" on:click={createOrder}>提交预约</button>
        </div>
      </div>
    {/if}

    {#each orders as o (o.id)}
      <div class="card order-item" class:active={selectedId === o.id} on:click={() => select(o.id)} role="button">
        <div class="row">
          <span class="no">{o.orderNo}</span>
          <span class="spacer"></span>
          <span class="badge {o.status}">{o.statusLabel}</span>
        </div>
        <div class="meta">{o.workContent}</div>
        <div class="meta">
          {o.scheduledStart} → {o.scheduledEnd}
          {#if o.scheduledStart.slice(0, 10) !== o.scheduledEnd.slice(0, 10)}<span class="tag">跨日</span>{/if}
        </div>
        <div class="meta">{o.customer.name} / {o.cleaner.name}</div>
        <div class="row amount">
          {#if o.bill.heldCents > 0}<span class="cents-held">挂起 {yuan(o.bill.heldCents)}</span>{/if}
          <span class="spacer"></span>
          <span>{yuan(o.bill.payableCents)}</span>
        </div>
      </div>
    {/each}
    {#if orders.length === 0}
      <div class="card muted small">{actor?.role === 'CLEANER' ? '暂无派给你的订单' : '暂无订单'}</div>
    {/if}
  </aside>

  <main class="detail">
    {#if order}
      <div class="card">
        <div class="row">
          <h2 style="margin:0;">{order.orderNo}</h2>
          <span class="badge {order.status}">{order.statusLabel}</span>
          <span class="spacer"></span>
          <span class="muted small">时薪 {yuan(order.rateCents)} · 迟到宽限 {order.lateGraceMinutes} 分钟</span>
        </div>
        <div class="kv mt12">
          <span class="k">客户</span><span>{order.customer.name}</span>
          <span class="k">钟点工</span><span>{order.cleaner.name}</span>
          <span class="k">预约时间</span>
          <span>{order.scheduledStart} → {order.scheduledEnd}
            {#if order.scheduledStart.slice(0, 10) !== order.scheduledEnd.slice(0, 10)}<span class="tag">跨日服务</span>{/if}
          </span>
          <span class="k">工作内容</span><span>{order.workContent}</span>
        </div>
        {#if order.perms.frozenByDispute}
          <div class="freeze-banner mt12">🔒 {order.perms.freezeReason}。客服裁定前，双方都无法新增或修改时间记录。</div>
        {/if}
      </div>

      <BillPanel {order} bill={order.bill} />
      <LogsPanel {order} actor={actor} refresh={refreshSelected} />
      <DisputePanel {order} refresh={refreshSelected} />
      <SettlementPanel {order} refresh={refreshSelected} />
    {:else}
      <div class="card muted">请从左侧选择一个订单查看详情。演示订单已预置：跨日计时、双方确认冲突、客户拒签、退款回执重复回传。</div>
    {/if}
  </main>
</div>

<div class="toast">
  {#each $toasts as t (t.id)}
    <div class="t {t.kind}">{t.message}</div>
  {/each}
</div>
