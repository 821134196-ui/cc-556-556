export const yuan = (cents) => `¥${(cents / 100).toFixed(2)}`;

export const signedYuan = (cents) => `${cents > 0 ? '+' : cents < 0 ? '-' : ''}¥${Math.abs(cents / 100).toFixed(2)}`;

export const mins = (m) => {
  if (m == null) return '—';
  const h = Math.floor(m / 60);
  const mm = m % 60;
  return h > 0 ? `${h} 小时 ${mm} 分钟` : `${mm} 分钟`;
};

// datetime-local 输入框 <-> "yyyy-MM-ddTHH:mm"
export function toLocalInput(s) {
  if (!s) return '';
  return s.replace(' ', 'T').slice(0, 16);
}
