<script>
  import { api } from '../api.js';
  import { toast } from './toast.js';

  export let order;
  export let actor;
  export let refresh;

  let clockType = 'ARRIVE';
  let clockAt = '';
  let clockNote = '';
  let outType = 'OUT';
  let outAt = '';
  let outNote = '';
  let confirmText = {};

  function nowInput() {
    const d = new Date();
    d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
    return d.toISOString().slice(0, 16);
  }

  async function doClock() {
    if (!clockAt) return toast('请选择打卡时间', 'err');
    try {
      await api.clock(order.id, { type: clockType, at: clockAt, note: clockNote });
      toast(`${clockType === 'ARRIVE' ? '到达' : '离开'}打卡已提交，等待对方确认`, 'ok');
      clockNote = '';
      await refresh();
    } catch (e) { toast(e.message, 'err'); }
  }

  async function doMidway() {
    if (!outAt) return toast('请选择时间', 'err');
    try {
      await api.midway(order.id, { type: outType, at: outAt, note: outNote });
      toast('中途' + (outType === 'OUT' ? '外出' : '返回') + '记录已登记', 'ok');
      outNote = '';
      await refresh();
    } catch (e) { toast(e.message, 'err'); }
  }

  async function doConfirm(log, accept) {
    try {
      await api.confirmLog(order.id, log.id, { accept, note: confirmText[log.id] || '' });
      toast(accept ? '已确认对方记录' : '已否认，系统自动挂起争议金额并通知客服', accept ? 'ok' : 'info');
      confirmText[log.id] = '';
      await refresh();
    } catch (e) { toast(e.message, 'err'); }
  }
</script>

<div class="card">
  <h2>服务记录（到达 / 离开 / 外出）</h2>
  <div class="muted small">记录一经提交时间不可修改；双方各执一词时先挂起争议，由客服裁定，任何一方不能直接改计时。</div>

  {#if order.perms.canClock || order.perms.party}
    <div class="grid2 mt12">
      {#if order.perms.canClock}
        <div style="border:1px solid var(--line);border-radius:8px;padding:10px;">
          <div class="small" style="font-weight:600;">钟点工打卡</div>
          <div class="row mt8">
            <select bind:value={clockType}>
              <option value="ARRIVE">到达</option>
              <option value="LEAVE">离开</option>
            </select>
            <input type="datetime-local" bind:value={clockAt} />
            <button class="small" on:click={doClock}>提交打卡</button>
          </div>
          <div class="mt8"><input style="width:100%" placeholder="备注（可选）" bind:value={clockNote} /></div>
        </div>
      {/if}
      {#if order.perms.party && order.status !== 'SETTLED' && !order.perms.frozenByDispute}
        <div style="border:1px solid var(--line);border-radius:8px;padding:10px;">
          <div class="small" style="font-weight:600;">登记中途外出 / 返回</div>
          <div class="row mt8">
            <select bind:value={outType}>
              <option value="OUT">中途外出</option>
              <option value="BACK">外出返回</option>
            </select>
            <input type="datetime-local" bind:value={outAt} />
            <button class="small ghost" on:click={doMidway}>登记</button>
          </div>
          <div class="mt8"><input style="width:100%" placeholder="事由（可选，如下楼取工具）" bind:value={outNote} /></div>
        </div>
      {/if}
    </div>
  {/if}

  <ul class="logs mt12">
    {#each order.logs as log (log.id)}
      <li class:conflict={log.status === 'REJECTED'} class:adjudicated={log.status === 'ADJUDICATED'}>
        <div class="log-head">
          <strong>{log.typeLabel}</strong>
          <span class="badge log-{log.status}">{log.statusLabel}</span>
          <span class="muted small">{log.reporter}（{log.reporterRole}）</span>
          <span class="spacer"></span>
          <span style="font-variant-numeric:tabular-nums;">{log.effectiveAt}</span>
          {#if log.adoptedAt && log.adoptedAt !== log.claimedAt}
            <span class="tag">主张 {log.claimedAt}</span>
          {/if}
        </div>
        <div class="log-body">
          {#if log.location}<span>📍 {log.location}</span>{/if}
          {#if log.note}<span>📝 {log.note}</span>{/if}
        </div>
        {#if log.canConfirm}
          <div class="row mt8">
            <input style="flex:1;min-width:160px;" placeholder="确认/否认说明（可选）"
                   bind:value={confirmText[log.id]} />
            <button class="small success" on:click={() => doConfirm(log, true)}>确认属实</button>
            <button class="small danger" on:click={() => doConfirm(log, false)}>否认（发起冲突）</button>
          </div>
        {/if}
      </li>
    {/each}
  </ul>
</div>
