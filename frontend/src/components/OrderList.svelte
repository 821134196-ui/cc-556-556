<script>
  import { createEventDispatcher } from 'svelte';
  import { api } from '../api.js';
  import { yuan, ROLE_TEXT, STATUS_BADGE } from '../lib/format.js';

  export let orders = [];
  export let currentUser;
  export let loading = false;

  const dispatch = createEventDispatcher();
  const notify = (msg, err, replay) => dispatch('toast', { msg, err, replay });

  let showCreate = false;
  let workers = [];
  let form = {
    workerId: '',
    scheduledStart: '',
    scheduledMinutes: 120,
    tasks: '',
    address: '',
    hourlyRateYuan: 50
  };

  async function toggleCreate() {
    showCreate = !showCreate;
    if (showCreate && workers.length === 0) {
      const users = await api.get('/users');
      workers = users.filter((u) => u.role === 'WORKER');
      if (!form.workerId && workers[0]) form.workerId = workers[0].id;
    }
  }

  async function submitOrder() {
    try {
      const created = await api.post('/orders', {
        customerId: currentUser.id,
        workerId: Number(form.workerId),
        scheduledStart: form.scheduledStart.replace('T', ' '),
        scheduledMinutes: Number(form.scheduledMinutes),
        tasks: form.tasks || '日常保洁',
        address: form.address || '客户家中（模拟地址）',
        hourlyRateFen: Math.round(Number(form.hourlyRateYuan) * 100)
      });
      showCreate = false;
      form.tasks = '';
      notify('下单成功，订单 #' + created.id);
      dispatch('changed');
    } catch (e) {
      notify(e.message, true);
    }
  }
</script>

<div class="topbar">
  <div>
    <h1>{ROLE_TEXT[currentUser.role]}工作台 · {currentUser.name}</h1>
    <div class="sub">
      {#if currentUser.role === 'WORKER'}
        仅显示分配给自己的订单
      {:else if currentUser.role === 'STAFF'}
        全部订单，可处理挂起争议（不可代客户确认完成、不可改计时）
      {:else}
        下单、确认到/离时间、确认完成并结算
      {/if}
    </div>
  </div>
  {#if currentUser.role === 'CUSTOMER'}
    <button class="primary" on:click={toggleCreate}>{showCreate ? '收起' : '＋ 预约钟点工'}</button>
  {/if}
</div>

{#if currentUser.role === 'CUSTOMER' && showCreate}
  <div class="panel create-form">
    <h3>填写预约信息</h3>
    <div class="form-grid">
      <label>
        服务员
        <select bind:value={form.workerId}>
          {#each workers as w}
            <option value={w.id}>{w.name}</option>
          {/each}
        </select>
      </label>
      <label>
        预约开始时间（跨日可直接选次日凌晨）
        <input type="datetime-local" bind:value={form.scheduledStart} />
      </label>
      <label>
        约定时长（分钟）
        <input type="number" min="30" step="30" bind:value={form.scheduledMinutes} />
      </label>
      <label>
        小时单价（元/小时）
        <input type="number" min="30" step="5" bind:value={form.hourlyRateYuan} />
      </label>
      <label class="wide">
        服务地址（本地模拟定位）
        <input placeholder="如：上海市浦东新区张杨路 500 号" bind:value={form.address} />
      </label>
      <label class="wide">
        工作内容
        <textarea rows="2" placeholder="如：厨房深度保洁、擦窗、拖地" bind:value={form.tasks}></textarea>
      </label>
    </div>
    <div class="actions">
      <button class="primary" on:click={submitOrder}
        disabled={!form.scheduledStart || !form.workerId}>提交预约</button>
      <button on:click={() => (showCreate = false)}>取消</button>
    </div>
  </div>
{/if}

{#if loading}
  <div class="muted" style="padding:20px">加载中…</div>
{:else if orders.length === 0}
  <div class="panel muted" style="padding:30px;text-align:center">暂无订单</div>
{:else}
  {#each orders as o}
    <div class="order-card" role="button" tabindex="0"
      on:click={() => dispatch('open', o.id)}
      on:keydown={(e) => e.key === 'Enter' && dispatch('open', o.id)}>
      <div class="oc-main">
        <div class="oc-title">
          订单 #{o.id} · {o.tasks}
          {#if o.disputed}<span class="badge disputed" style="margin-left:8px">争议挂起</span>{/if}
        </div>
        <div class="oc-meta">
          <span>客户：{o.customerName}</span>
          <span>服务员：{o.workerName}</span>
          <span>预约：{o.scheduledStart}（{o.scheduledMinutes} 分钟）</span>
        </div>
      </div>
      <div class="oc-right">
        <span class="badge {STATUS_BADGE[o.status] || 'booked'}">{o.status}</span>
      </div>
    </div>
  {/each}
{/if}

<style>
  .form-grid {
    display: grid; grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px 14px;
  }
  .form-grid label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: var(--ink-2); }
  .form-grid .wide { grid-column: 1 / -1; }
  .form-grid input, .form-grid select, .form-grid textarea { width: 100%; }
</style>
