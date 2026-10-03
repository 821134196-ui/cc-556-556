<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import OrderList from './components/OrderList.svelte';
  import OrderDetail from './components/OrderDetail.svelte';

  let users = [];
  let currentUser = null;
  let orders = [];
  let selectedId = null;
  let toast = null;
  let toastTimer = null;
  let loading = false;

  onMount(loadUsers);

  async function loadUsers() {
    users = await api.get('/users');
    if (!currentUser && users.length) {
      currentUser = users.find((u) => u.role === 'STAFF') || users[0];
      await refresh();
    }
  }

  async function refresh() {
    if (!currentUser) return;
    loading = true;
    try {
      orders = await api.get(
        `/orders?viewerId=${currentUser.id}&viewerRole=${currentUser.role}`
      );
    } catch (e) {
      showToast(e.message, true);
    } finally {
      loading = false;
    }
  }

  async function switchUser(u) {
    currentUser = u;
    selectedId = null;
    await refresh();
  }

  function openOrder(id) {
    selectedId = id;
  }

  function back() {
    selectedId = null;
  }

  function showToast(message, isError = false, replay = false) {
    toast = { message, isError, replay };
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => (toast = null), 4200);
  }

  const grouped = () => {
    const g = { STAFF: [], CUSTOMER: [], WORKER: [] };
    for (const u of users) (g[u.role] ||= []).push(u);
    return g;
  };

  const ROLE_LABEL = { STAFF: '客服', CUSTOMER: '客户', WORKER: '服务员' };
</script>

<div class="app">
  <aside class="sidebar">
    <div class="logo">🏠 家政钟点工<br />时长与结算系统</div>

    {#each ['STAFF', 'CUSTOMER', 'WORKER'] as role}
      <div class="group-label">{ROLE_LABEL[role]}</div>
      {#each grouped()[role] || [] as u}
        <div
          class="person"
          class:active={currentUser && currentUser.id === u.id}
          role="button"
          tabindex="0"
          on:click={() => switchUser(u)}
          on:keydown={(e) => e.key === 'Enter' && switchUser(u)}
        >
          <span>{u.name}</span>
          <span class="role">{u.phone || role}</span>
        </div>
      {/each}
    {/each}

    <div class="spacer"></div>
    <div class="hint">
      左侧切换身份模拟登录：服务员只能看自己的订单，客服可处理全部争议但不能代客户确认完成。
    </div>
  </aside>

  <main class="main">
    {#if toast}
      <div class="toast" class:error={toast.isError} class:replay={toast.replay}>
        {toast.message}
      </div>
    {/if}

    {#if selectedId}
      <OrderDetail
        {selectedId}
        currentUser={currentUser}
        onBack={back}
        onToast={showToast}
        onChanged={refresh}
      />
    {:else if currentUser}
      <OrderList
        {orders}
        {currentUser}
        {loading}
        onOpen={openOrder}
        onToast={showToast}
        onChanged={refresh}
      />
    {/if}
  </main>
</div>
