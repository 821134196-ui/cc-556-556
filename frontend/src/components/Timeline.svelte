<script>
  export let events = [];

  const TYPE_TEXT = {
    ARRIVAL: '到达打卡',
    DEPARTURE: '离开打卡',
    PAUSE_OUT: '中途外出',
    RETURN: '服务返回'
  };
  const ROLE_TEXT = { CUSTOMER: '客户', WORKER: '服务员', STAFF: '客服', SYSTEM: '系统' };
</script>

<ul class="timeline">
  {#each events as e}
    <li class={e.actorRole.toLowerCase()}>
      <span class="t-time">{e.clockAtText}</span>
      <span class="t-who">
        {TYPE_TEXT[e.eventType] || e.eventType} · {ROLE_TEXT[e.actorRole] || e.actorRole}
        {e.actorName ? `（${e.actorName}）` : ''} · {e.action === 'REPORTED' ? '上报' : '确认'}
      </span>
      {#if e.location}<span class="t-loc">📍 {e.location}</span>{/if}
      {#if e.note}<div class="t-note">备注：{e.note}</div>{/if}
    </li>
  {/each}
</ul>
