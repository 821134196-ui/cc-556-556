// 简单的 API 封装：所有请求带 X-Actor-Id 模拟当前登录身份
const ACTOR_KEY = 'cleaning.actorId';

export function getActorId() {
  return localStorage.getItem(ACTOR_KEY) ? Number(localStorage.getItem(ACTOR_KEY)) : null;
}
export function setActorId(id) {
  if (id) localStorage.setItem(ACTOR_KEY, String(id));
  else localStorage.removeItem(ACTOR_KEY);
}

async function request(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(getActorId() ? { 'X-Actor-Id': String(getActorId()) } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(data?.error || `请求失败（${res.status}）`);
  return data;
}

export const api = {
  users: () => request('GET', '/api/users'),
  orders: () => request('GET', '/api/orders'),
  order: (id) => request('GET', `/api/orders/${id}`),
  createOrder: (body) => request('POST', '/api/orders', body),
  clock: (id, body) => request('POST', `/api/orders/${id}/clock`, body),
  midway: (id, body) => request('POST', `/api/orders/${id}/midway`, body),
  confirmLog: (id, logId, body) => request('POST', `/api/orders/${id}/logs/${logId}/confirm`, body),
  complete: (id, body) => request('POST', `/api/orders/${id}/complete`, body),
  dispute: (id, body) => request('POST', `/api/orders/${id}/disputes`, body),
  resolve: (disputeId, body) => request('POST', `/api/orders/disputes/${disputeId}/resolve`, body),
  settle: (id, idempotencyKey) => request('POST', `/api/orders/${id}/settle`, { idempotencyKey }),
  refund: (id, body) => request('POST', `/api/orders/${id}/refunds`, body),
  approveRefund: (refundId) => request('POST', `/api/orders/refunds/${refundId}/approve`),
  refundCallback: (body) => request('POST', '/api/orders/refunds/callback', body)
};
