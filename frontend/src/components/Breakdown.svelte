<script>
  import { yuan, signedYuan } from '../lib/format.js';

  export let breakdown;
  export let title = '费用拆解';
  export let compact = false;

  const EVENT_TEXT = {
    NORMAL: '按时完成',
    OVERTIME: '临时加活 · 加时',
    EARLY: '提前结束 · 按实计费'
  };
</script>

{#if breakdown}
  <div class="panel breakdown">
    <h3>
      {title}
      {#if breakdown.scenario}
        <span class="scenario">{EVENT_TEXT[breakdown.scenario] || breakdown.scenario}</span>
      {/if}
      {#if breakdown.crossDay}
        <span class="crossday-tag">跨日服务 · 共 {breakdown.dayCount} 个自然日</span>
      {/if}
    </h3>

    <table class="bill-table">
      <tbody>
        {#each breakdown.lines as line}
          <tr>
            <td>
              {line.label}
              <div class="detail">{line.detail}</div>
            </td>
            <td class="amt {line.amountFen > 0 ? 'plus' : line.amountFen < 0 ? 'minus' : 'zero'}">
              {line.amountFen === 0 ? '—' : signedYuan(line.amountFen) + ' 元'}
            </td>
          </tr>
        {/each}
        <tr class="bill-total">
          <td>应收合计</td>
          <td class="amt">{yuan(breakdown.payableFen)} 元</td>
        </tr>
      </tbody>
    </table>

    {#if !compact}
      <div class="time-summary">
        <span>
          实际到达：<b>{breakdown.arrivalText || '未确认'}</b>
        </span>
        <span>
          实际离开：<b>{breakdown.departureText || '未确认'}</b>
        </span>
        {#if breakdown.workedMinutes !== null && breakdown.workedMinutes !== undefined}
          <span>实际工作：<b>{breakdown.workedMinutes} 分钟</b>（{Math.floor(breakdown.workedMinutes / 60)} 小时 {breakdown.workedMinutes % 60} 分）</span>
        {/if}
        {#if breakdown.pauseMinutes > 0}
          <span class="pause">外出暂停：{breakdown.pauseMinutes} 分钟（不计费）</span>
        {/if}
        <span>
          约定时长：{breakdown.scheduledMinutes} 分钟
          {#if breakdown.lateMinutes > 0}
            · 迟到 <b class="minus">{breakdown.lateMinutes} 分钟</b>
          {/if}
        </span>
      </div>
    {/if}
  </div>
{/if}

<style>
  .scenario {
    font-size: 12px; font-weight: 600; color: var(--brand);
    background: var(--brand-soft); border-radius: 6px; padding: 2px 8px; margin-left: 8px;
  }
  .time-summary {
    display: flex; flex-wrap: wrap; gap: 6px 18px;
    margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--line);
    font-size: 12.5px; color: var(--ink-2);
  }
  .time-summary b { color: var(--ink); }
  .pause { color: var(--purple); }
</style>
