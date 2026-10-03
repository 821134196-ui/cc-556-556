<script>
  import { api } from '../api.js';
  import { toast } from './toast.js';
  import { yuan, toLocalInput } from './format.js';

  export let order;
  export let refresh;

  const reasons = [
    ['TIME_CONFLICT', '到达/离开时间双方说法不一致'],
    ['LEFT_MIDWAY', '服务员中途离岗'],
    ['REJECT_SIGN', '客户拒签确认完成'],
    ['WORK_QUALITY', '完成情况/质量异议'],
    ['EXTRA_WORK', '临时加活费用异议']
  ];

  let reason = 'WORK_QUALITY';
  let description = '';
  let heldCents = '';

  // 客服裁定表单（每个挂起争议一份）
  let form = {};
  $: order.disputes
    .filter((d) => d.status === 'OPEN' && !form[d.id])
    .forEach((d) => (form[d.id] = initForm(d)));
  function initForm(d) {
    return {
      adoptedArriveAt: d.adoptedArriveAt ? toLocalInput(d.adoptedArriveAt) : '',
      adoptedLeaveAt: d.adoptedLeaveAt ? toLocalInput(d.adoptedLeaveAt) : '',
      serviceAccepted: '',
      deductMinutes: '',
      approvedExtraCents: '',
      releaseToCleanerCents: '',
      refundToCustomerCents: '',
      resolution: ''
    };
  }

  async function raise() {
    try {
      const body = {
        reason,
        description,
        heldCents: heldCents === '' ? null : Math.round(Number(heldCents) * 100)
      };
      await api.dispute(order.id, body);
      toast('争议已发起，争议金额挂起、计时冻结，等待客服处理', 'info');
      description = ''; heldCents = '';
      await refresh();
    } catch (e) { toast(e.message, 'err'); }
  }

  async function resolve(d) {
    const f = form[d.id];
    try {
      const body = {
        adoptedArriveAt: form[d.id].adoptedArriveAt || null,
        adoptedLeaveAt: form[d.id].adoptedLeaveAt || null,
        serviceAccepted: form[d.id].serviceAccepted === '' ? null : form[d.id].serviceAccepted === 'true',
        deductMinutes: form[d.id].deductMinutes === '' ? null : Number(form[d.id].deductMinutes),
        approvedExtraCents: form[d.id].approvedExtraCents === '' ? null : Math.round(Number(form[d.id].approvedExtraCents) * 100),
        releaseToCleanerCents: form[d.id].releaseToCleanerCents === '' ? null : Math.round(Number(form[d.id].releaseToCleanerCents) * 100),
        refundToCustomerCents: form[d.id].refundToCustomerCents === '' ? null : Math.round(Number(form[d.id].refundToCustomerCents) * 100),
        resolution: form[d.id].resolution
      };
      await api.resolve(d.id, body);
      toast('争议已裁定，挂起金额按裁定处理，计费已更新', 'ok');
      await refresh();
    } catch (e) { toast(e.message, 'err'); }
  }
</script>

<div class="card">
  <h2>争议与挂起金额</h2>

  {#if order.disputes.length === 0}
    <div class="muted small">暂无争议。</div>
  {/if}
  <ul class="disputes">
    {#each order.disputes as d (d.id)}
      <li class:open={d.status === 'OPEN'} class:resolved={d.status === 'RESOLVED'}>
        <div class="log-head">
          <strong>{d.reasonLabel}</strong>
          <span class="badge {d.status === 'OPEN' ? 'log-PENDING' : 'log-CONFIRMED'}">{d.statusLabel}</span>
          <span class="muted small">{d.raisedBy}（{d.raisedByRole}）发起于 {d.createdAt}</span>
          <span class="spacer"></span>
          <strong style="color:var(--amber)">挂起 {yuan(d.heldCents)}</strong>
        </div>
        <div class="small mt8">{d.description}</div>

        {#if d.status === 'RESOLVED'}
          <hr class="sep" />
          <div class="kv small">
            <span class="k">处理客服</span><span>{d.resolvedBy} · {d.resolvedAt}</span>
            {#if d.adoptedArriveAt}<span class="k">采纳到达</span><span>{d.adoptedArriveAt}</span>{/if}
            {#if d.adoptedLeaveAt}<span class="k">采纳离开</span><span>{d.adoptedLeaveAt}</span>{/if}
            {#if d.serviceAccepted !== null && d.serviceAccepted !== undefined}
              <span class="k">完成认定</span><span>{d.serviceAccepted ? '认定按约完成' : '认定未完成'}</span>
            {/if}
            {#if d.deductMinutes}<span class="k">工时扣减</span><span>{d.deductMinutes} 分钟</span>{/if}
            {#if d.approvedExtraCents}<span class="k">核准加活</span><span class="cents-plus">+{yuan(d.approvedExtraCents)}</span>{/if}
            {#if d.releaseToCleanerCents}<span class="k">释放给钟点工</span><span>{yuan(d.releaseToCleanerCents)}</span>{/if}
            {#if d.refundToCustomerCents}<span class="k">退给客户</span><span class="cents-minus">{yuan(d.refundToCustomerCents)}</span>{/if}
            {#if d.resolution}<span class="k">裁定说明</span><span>{d.resolution}</span>{/if}
          </div>
        {:else if order.perms.canResolveDisputes}
          <hr class="sep" />
          <div class="small" style="font-weight:600;margin-bottom:6px;">客服裁定（依据双方记录，仅客服可提交）</div>
          <div class="grid3">
            <label class="field">采纳到达时间<input type="datetime-local" bind:value={form[d.id].adoptedArriveAt} /></label>
            <label class="field">采纳离开时间<input type="datetime-local" bind:value={form[d.id].adoptedLeaveAt} /></label>
            <label class="field">是否认定完成
              <select bind:value={form[d.id].serviceAccepted}>
                <option value="">不涉及</option>
                <option value="true">认定按约完成</option>
                <option value="false">认定未完成</option>
              </select>
            </label>
            <label class="field">工时扣减（分钟）<input type="number" min="0" bind:value={form[d.id].deductMinutes} /></label>
            <label class="field">核准加活费（元）<input type="number" min="0" step="0.01" bind:value={form[d.id].approvedExtraCents} /></label>
            <label class="field">挂起释放给钟点工（元，≤ {yuan(d.heldCents)}）
              <input type="number" min="0" step="0.01" bind:value={form[d.id].releaseToCleanerCents} />
            </label>
            <label class="field">挂起退给客户（元，≤ {yuan(d.heldCents)}）
              <input type="number" min="0" step="0.01" bind:value={form[d.id].refundToCustomerCents} />
            </label>
          </div>
          <label class="field mt8">裁定说明<textarea bind:value={form[d.id].resolution} placeholder="如：结合门禁记录与双方聊天记录，采纳 09:35 到达……"></textarea></label>
          <div class="mt8"><button class="small" on:click={() => resolve(d)}>提交裁定并解冻订单</button></div>
        {/if}
      </li>
    {/each}
  </ul>

  {#if order.perms.canRaiseDispute}
    <hr class="sep" />
    <div class="small" style="font-weight:600;">发起争议（客户或钟点工）</div>
    <div class="grid2 mt8">
      <label class="field">争议类型
        <select bind:value={reason}>
          {#each reasons as [value, label]}<option {value}>{label}</option>{/each}
        </select>
      </label>
      <label class="field">挂起金额（元，留空自动估算）<input type="number" min="0" step="0.01" bind:value={heldCents} /></label>
    </div>
    <label class="field mt8"><textarea bind:value={description} placeholder="描述争议经过与你的主张"></textarea></label>
    <div class="mt8"><button class="small ghost" on:click={raise}>挂起争议金额，交客服处理</button></div>
  {/if}
</div>
