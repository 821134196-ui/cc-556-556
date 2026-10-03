export function yuan(fen) {
  if (fen === null || fen === undefined) return '—';
  return (fen / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function signedYuan(fen) {
  const y = yuan(Math.abs(fen));
  return fen > 0 ? `+${y}` : fen < 0 ? `-${y}` : y;
}

/** datetime-local（本地时间，页面统一按北京时间演示）→ epochMillis */
export function toEpoch(value) {
  if (!value) return null;
  return new Date(value.replace(' ', 'T')).getTime();
}

/** epochMillis → datetime-local 输入框值 */
export function toInputValue(epoch) {
  if (!epoch) return '';
  const d = new Date(epoch);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export const ROLE_TEXT = { CUSTOMER: '客户', WORKER: '服务员', STAFF: '客服' };

export const STATUS_BADGE = {
  BOOKED: 'booked',
  IN_PROGRESS: 'progress',
  DISPUTED: 'disputed',
  COMPLETED: 'completed',
  SETTLED: 'settled'
};
