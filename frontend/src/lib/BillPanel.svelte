<script>
  import { yuan, signedYuan, mins } from './format.js';

  export let bill;
  export let order;

  $: crossDay = order.scheduledStart.slice(0, 10) !== order.scheduledEnd.slice(0, 10);

  function cls(kind, cents) {
    if (kind === 'extra' || cents > 0) return 'cents-plus';
    if (kind === 'held') return 'cents-held';
    if (cents < 0) return 'cents-minus';
    return 'cents-zero';
  }
</script>

<div class="card">
  <h2>时长与费用</h2>
  <div class="summary-tiles">
    <div class="tile" class:crossday>
      <div class="k">约定时长{crossDay ? '（跨日）' : ''}</div>
      <div class="v">{mins(bill.scheduledMinutes)}</div>
    </div>
    <div class="tile">
      <div class="k">实际在岗（扣外出）</div>
      <div class="v">{bill.arriveAt ? mins(bill.actualMinutes) : '—'}</div>
    </div>
    <div class="tile">
      <div class="k">迟到</div>
      <div class="v" class:cents-minus={bill.lateMinutes > order.lateGraceMinutes}>
        {bill.lateMinutes ? `${bill.lateMinutes} 分钟` : '—'}
      </div>
    </div>
    <div class="tile">
      <div class="k">提前结束</div>
      <div class="v" class:cents-minus={bill.earlyMinutes > 0}>
        {bill.earlyMinutes ? `${bill.earlyMinutes} 分钟` : '—'}
      </div>
    </div>
    <div class="tile">
      <div class="k">加时</div>
      <div class="v" class:cents-plus={bill.overtimeMinutes > 0}>
        {bill.overtimeMinutes ? `${bill.overtimeMinutes} 分钟` : '—'}
      </div>
    </div>
    <div class="tile">
      <div class="k">当前应付</div>
      <div class="v">{yuan(bill.payableCents)}</div>
    </div>
  </div>

  <table class="bill mt12">
    <tbody>
      {#each bill.items as item}
        <tr>
          <td class="muted small">{item.label}</td>
          <td class="amount {cls(item.kind, item.cents)}">
            {item.kind === 'note' && item.cents === 0 ? '不扣款' : signedYuan(item.cents)}
          </td>
        </tr>
      {/each}
      <tr class="total">
        <td>{bill.heldCents > 0 ? '当前应付（争议挂起金额冻结中）' : '应付合计'}</td>
        <td class="amount">{yuan(bill.payableCents)}</td>
      </tr>
    </tbody>
  </table>
  <div class="muted small mt8">
    计费规则：迟到超过 {order.lateGraceMinutes} 分钟宽限按分钟扣款；提前结束少做的分钟按单价扣回；
    中途外出时段不计费；客户确认完成后加时按单价计；临时加活异议由客服核准；
    跨日服务按实际起止时间差计算。
  </div>
</div>
