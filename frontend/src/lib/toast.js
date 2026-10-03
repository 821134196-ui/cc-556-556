import { writable } from 'svelte/store';

export const toasts = writable([]);
let seq = 1;

export function toast(message, kind = 'ok', timeout = 3500) {
  const id = seq++;
  toasts.update((ts) => [...ts, { id, message, kind }]);
  setTimeout(() => toasts.update((ts) => ts.filter((t) => t.id !== id)), timeout);
}
