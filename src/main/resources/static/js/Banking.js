const BANKING_CONFIG = {
  API_BASE_URL: `${window.location.origin}/api`,
  PROFILE_ENDPOINT: "/user/me",
  ACCOUNTS_ENDPOINT: "/accounts/me",
  ACCOUNT_CREATE_ENDPOINT: "/accounts/create",
  ACCOUNT_LOOKUP_ENDPOINT: "/accounts/lookup",
  SAVED_RECEIVERS_ENDPOINT: "/saved-receivers",
  MY_BALANCE_ENDPOINT: "/reports/my-balance",
  RECENT_TRANSACTIONS_ENDPOINT: "/reports/my-transactions?page=0&size=5",
  TRANSFER_ENDPOINT: "/v1/transfer/execute",
};

const transferPageState = {
  savedReceivers: [],
};

const dashboardState = {
  savedReceivers: [],
};

const DEFAULT_NOTIFICATIONS = [
  {
    title: "Ưu đãi hoàn tiền 10%",
    subtitle: "Áp dụng cho thanh toán hóa đơn",
  },
  {
    title: "Bảo mật tài khoản",
    subtitle: "Hãy bật xác thực 2 lớp để an toàn hơn",
  },
];

document.addEventListener("DOMContentLoaded", () => {
  if (document.getElementById("recentTransactions")) {
    initDashboardPage();
  }

  if (document.getElementById("transferForm")) {
    initTransferPage();
  }
});

async function initDashboardPage() {
  const isAuthenticated = hasAuthToken();
  setupDashboardProfileMenu(isAuthenticated);

  const storedUser = readStoredUser();
  if (storedUser && isAuthenticated) {
    hydrateUserHeader(storedUser);
  }

  if (!isAuthenticated) {
    renderGuestDashboard();
    return;
  }

  const [profileResult, accountsResult, balanceResult, recentTransactionsResult] = await Promise.all([
    fetchJson(BANKING_CONFIG.PROFILE_ENDPOINT),
    fetchJson(BANKING_CONFIG.ACCOUNTS_ENDPOINT),
    fetchJson(BANKING_CONFIG.MY_BALANCE_ENDPOINT),
    fetchJson(BANKING_CONFIG.RECENT_TRANSACTIONS_ENDPOINT),
  ]);

  const user = extractUser(profileResult.data) || storedUser;
  const accounts = extractAccounts(accountsResult.data);
  const backendBalance = extractBalance(balanceResult.data);
  const recentTransactions = extractRecentTransactions(recentTransactionsResult.data);
  const primaryAccount = pickPrimaryAccount(accounts);
  const savingsAccount = pickSavingsAccount(accounts);

  if (user) {
    storeUser(user);
    hydrateUserHeader(user);
  }

  bindAccountRegistrationForm();
  bindDashboardReceiverActions();
  toggleAccountRegistrationSection(!accounts.length);
  renderDashboardSummary(accounts, primaryAccount, savingsAccount, recentTransactions, backendBalance);
  renderRecentTransactions(recentTransactions);
  await loadDashboardSavedReceivers();

  if (!accountsResult.ok && !balanceResult.ok) {
    showBanner(
      "dashboardStatus",
      "Không lấy được dữ liệu tài khoản từ backend. Dashboard đang hiển thị số dư dự phòng.",
      "warning",
    );
  } else if (!accounts.length && !Number.isFinite(backendBalance) && !recentTransactionsResult.ok) {
    showBanner(
      "dashboardStatus",
      "Chưa có dữ liệu tài khoản và giao dịch để hiển thị trên dashboard.",
      "warning",
    );
  } else if (!recentTransactionsResult.ok) {
    showBanner(
      "dashboardStatus",
      "Không lấy được giao dịch gần đây từ backend.",
      "warning",
    );
  } else {
    hideBanner("dashboardStatus");
  }

  renderAccounts(accounts);
  renderNotifications(DEFAULT_NOTIFICATIONS);
}

async function initTransferPage() {
  if (!requireAuth()) return;

  const form = document.getElementById("transferForm");
  const amountInput = document.getElementById("amount");
  const submitBtn = document.getElementById("transferSubmitBtn");
  const receiverAccountInput = document.getElementById("receiverAccount");
  const bankCodeSelect = document.getElementById("bankCode");
  const savedAccountsList = document.getElementById("savedReceiverAccountsList");
  const saveReceiverBtn = document.getElementById("saveReceiverBtn");
  let receiverLookup = null;

  const [profileResult, accountsResult] = await Promise.all([
    fetchJson(BANKING_CONFIG.PROFILE_ENDPOINT),
    fetchJson(BANKING_CONFIG.ACCOUNTS_ENDPOINT),
  ]);

  const user = extractUser(profileResult.data) || readStoredUser();
  const accounts = extractAccounts(accountsResult.data);
  const primaryAccount = pickPrimaryAccount(accounts);

  if (user) {
    storeUser(user);
    setText("transferSourceName", user.fullName || user.name || user.username || "Chủ tài khoản");
  }

  if (primaryAccount) {
    setText("transferSourceAccount", maskAccountNumber(primaryAccount.accountNumber));
    setText("transferSourceBalance", formatCurrency(primaryAccount.balance));
  }

  populateTransferBankOptions(bankCodeSelect);
  await loadSavedReceiverAccounts(receiverAccountInput);

  const presetAccountNumber = new URLSearchParams(window.location.search).get("accountNumber");
  if (presetAccountNumber) {
    receiverAccountInput.value = String(presetAccountNumber).replace(/[^\d]/g, "");
    receiverLookup = await lookupReceiverAccount(receiverAccountInput.value);
  }

  document.querySelectorAll("[data-quick-amount]").forEach((button) => {
    button.addEventListener("click", () => {
      const current = Number(amountInput.value || 0);
      const increment = Number(button.dataset.quickAmount || 0);
      amountInput.value = String(current + increment);
    });
  });

  receiverAccountInput.addEventListener("input", () => {
    receiverAccountInput.value = receiverAccountInput.value.replace(/[^\d]/g, "");
    receiverLookup = null;
    clearTransferMessages();
  });

  receiverAccountInput.addEventListener("blur", async () => {
    receiverLookup = await lookupReceiverAccount(receiverAccountInput.value);
  });

  savedAccountsList?.addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-account-number]");
    if (!button) {
      return;
    }

    const accountNumber = button.dataset.accountNumber || "";
    receiverAccountInput.value = accountNumber;
    receiverLookup = await lookupReceiverAccount(accountNumber);
  });

  saveReceiverBtn?.addEventListener("click", async () => {
    const accountNumber = receiverLookup?.accountNumber || receiverAccountInput.value;
    const normalizedAccountNumber = String(accountNumber || "").trim().replace(/[^\d]/g, "");
    if (!normalizedAccountNumber) {
      return;
    }

    setButtonState(saveReceiverBtn, true, "Đang lưu...");
    const savedItem = await saveReceiverAccount(normalizedAccountNumber);

    if (!savedItem) {
      setButtonState(saveReceiverBtn, false, "Lưu");
      showBanner("transferStatus", "Không thể lưu người nhận này.", "error");
      return;
    }

    await loadSavedReceiverAccounts(receiverAccountInput);
    updateSaveReceiverButton(normalizedAccountNumber);
    showBanner("transferStatus", "Đã lưu người nhận gần đây.", "success");
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearTransferMessages();

    const payload = {
      toAccountNumber: receiverAccountInput.value.trim(),
      amount: Number(amountInput.value),
      description: document.getElementById("transferDescription").value.trim(),
    };

    if (!validateTransferPayload(payload, primaryAccount, receiverLookup)) {
      return;
    }

    setButtonState(submitBtn, true, "Đang xử lý...");

    const transferResult = await fetchJson(BANKING_CONFIG.TRANSFER_ENDPOINT, {
      method: "POST",
      body: JSON.stringify(payload),
    });

    if (!transferResult.ok) {
      handleTransferApiError(transferResult.data);
      showBanner("transferStatus", transferResult.message || "Chuyển khoản thất bại.", "error");
      setButtonState(submitBtn, false, "Tiếp tục");
      return;
    }

    showBanner(
      "transferStatus",
      transferResult.message || transferResult.data?.data?.message || "Chuyển khoản thành công.",
      "success",
    );

    form.reset();
    receiverLookup = null;
    ["receiverAccountError", "bankCodeError", "amountError", "descriptionError", "receiverName"].forEach((id) => {
      setText(id, "");
    });
    updateSaveReceiverButton("");
    renderSavedReceiverAccounts(receiverAccountInput);

    if (primaryAccount) {
      primaryAccount.balance = Math.max(0, Number(primaryAccount.balance || 0) - payload.amount);
      setText("transferSourceBalance", formatCurrency(primaryAccount.balance));
    }

    setButtonState(submitBtn, false, "Tiếp tục");
  });
}

function renderDashboardSummary(accounts, primaryAccount, savingsAccount, recentTransactions = [], backendBalance = NaN) {
  const totalBalance = accounts.length ? sumAccountBalances(accounts) : normalizeAmount(backendBalance);
  const sentTransactions = recentTransactions.filter((item) => normalizeDirection(item.direction) === "OUT");
  const receivedTransactions = recentTransactions.filter((item) => normalizeDirection(item.direction) === "IN");
  const sentAmount = sentTransactions.reduce((total, item) => total + normalizeAmount(item.amount), 0);
  const receivedAmount = receivedTransactions.reduce((total, item) => total + normalizeAmount(item.amount), 0);
  const currentBalance = primaryAccount ? normalizeAmount(primaryAccount.balance) : totalBalance;

  setText("heroTotalBalance", formatCurrency(totalBalance));
  setText("heroLastUpdated", `Cập nhật lần cuối: ${formatDateTime(new Date())}`);
  setText("heroPrimaryAccount", maskAccountNumber(primaryAccount?.accountNumber));
  setText("heroSavingsBalance", formatCurrency(savingsAccount?.balance || 0));

  setText("summaryCurrentBalance", formatCurrency(currentBalance));
  setText(
    "summaryCurrentBalanceHint",
    `${accounts.length} tài khoản đang hoạt động`,
  );
  setText("summarySentAmount", formatCurrency(sentAmount));
  setText("summarySentCount", `${sentTransactions.length} giao dịch chuyển đi`);
  setText("summaryReceivedAmount", formatCurrency(receivedAmount));
  setText("summaryReceivedCount", `${receivedTransactions.length} giao dịch nhận tiền`);
  setText("summaryTransactionCount", String(recentTransactions.length));
  setText(
    "summaryTransactionHint",
    recentTransactions.length
      ? "Tổng hợp từ các giao dịch gần đây lấy từ backend"
      : "Chưa có giao dịch gần đây để hiển thị",
  );
}

async function fetchJson(endpoint, options = {}) {
  try {
    const response = await fetch(buildApiUrl(endpoint), withAuthHeaders(options));
    const data = await parseJsonSafe(response);

    return {
      ok: response.ok,
      status: response.status,
      data,
      message: data?.message,
    };
  } catch (error) {
    console.error(`API error for ${endpoint}:`, error);
    return {
      ok: false,
      status: 0,
      data: null,
      message: "Không thể kết nối tới backend.",
    };
  }
}

function hasAuthToken() {
  return Boolean(localStorage.getItem("token"));
}

function requireAuth() {
  if (hasAuthToken()) {
    return true;
  }

  window.location.href = "/login";
  return false;
}

function withAuthHeaders(options = {}) {
  const token = localStorage.getItem("token");
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return { ...options, headers };
}

function buildApiUrl(endpoint) {
  return `${BANKING_CONFIG.API_BASE_URL}${endpoint}`;
}

function extractUser(data) {
  return data?.user || data?.data?.user || data?.data || data || null;
}

function extractAccounts(data) {
  const accounts = data?.accounts || data?.data?.accounts || data?.data || data;
  if (!Array.isArray(accounts)) {
    return [];
  }

  return accounts.map((account, index) => ({
    id: account.id || "",
    accountNumber: account.accountNumber || "",
    balance: Number(account.balance || 0),
    type: account.type || "",
    name: account.name || "Tài khoản",
    primary: Boolean(account.primary) || index === 0,
    status: account.status || "",
  }));
}

function extractBalance(data) {
  const value = data?.balance ?? data?.data?.balance ?? data?.data ?? data;
  return Number(value);
}

function extractRecentTransactions(data) {
  const items = data?.items || data?.data?.items || data?.data || data;
  if (!Array.isArray(items)) {
    return [];
  }

  return items.map((item) => ({
    transactionId: item.transactionId || "",
    direction: item.direction || "",
    counterpartyAccount: item.counterpartyAccount || "",
    amount: normalizeAmount(item.amount),
    description: item.description || "",
    status: item.status || "",
    createdAt: item.createdAt || null,
  }));
}

function extractLookupAccount(data) {
  return data?.account || data?.data?.account || data?.data || data || null;
}

function pickPrimaryAccount(accounts) {
  return accounts.find((account) => account.primary) || accounts[0] || null;
}

function pickSavingsAccount(accounts) {
  return accounts.find((account) => isSavingsType(account.type) || isSavingsType(account.name)) || null;
}

function isSavingsType(value) {
  const normalized = String(value || "").toLowerCase();
  return normalized.includes("tiết kiệm") || normalized.includes("save");
}

function hydrateUserHeader(user) {
  if (!user) return;

  const name = user.fullName || user.name || user.username || "Tên người dùng";
  setText("headerUserName", name);
  setText("headerUserTier", resolveUserSubtitle(user));
  setText("userInitials", getInitials(name));
}

function setupDashboardProfileMenu(isAuthenticated) {
  const menuButton = document.getElementById("dashboardProfileMenuBtn");
  const menu = document.getElementById("dashboardProfileMenu");
  const items = document.getElementById("dashboardProfileMenuItems");
  if (!menuButton || !menu || !items) return;

  items.innerHTML = isAuthenticated
    ? `
      <button
        id="dashboardLogoutBtn"
        type="button"
        class="flex w-full items-center justify-between rounded-xl px-4 py-3 text-left font-semibold text-rose-600 transition hover:bg-rose-50"
      >
        <span>Đăng xuất</span>
        <span>↗</span>
      </button>
    `
    : `
      <a href="/login" class="flex items-center justify-between rounded-xl px-4 py-3 font-semibold text-slate-700 transition hover:bg-indigo-50 hover:text-indigo-600">
        <span>Đăng nhập</span>
        <span>↗</span>
      </a>
      <a href="/register" class="flex items-center justify-between rounded-xl px-4 py-3 font-semibold text-cyan-700 transition hover:bg-cyan-50">
        <span>Đăng ký</span>
        <span>↗</span>
      </a>
    `;

  menuButton.addEventListener("click", (event) => {
    event.stopPropagation();
    menu.classList.toggle("hidden");
  });

  document.addEventListener("click", (event) => {
    if (!menu.contains(event.target) && !menuButton.contains(event.target)) {
      menu.classList.add("hidden");
    }
  });

  items.querySelector("#dashboardLogoutBtn")?.addEventListener("click", () => {
    clearAuthData();
    window.location.href = "/dashboard";
  });
}

function renderGuestDashboard() {
  setText("headerUserName", "Khách");
  setText("headerUserTier", "Vui lòng đăng nhập để xem đầy đủ tính năng");
  setText("userInitials", "KH");
  hideBanner("dashboardStatus");
  toggleAccountRegistrationSection(false);
  renderRecentTransactions([]);
  renderAccounts([]);
  renderNotifications(DEFAULT_NOTIFICATIONS);
  renderDashboardSavedReceivers([]);
  setText("dashboardRecentReceiverCount", "0");
  setText("dashboardRecentReceiverSynced", "0");
}

function bindAccountRegistrationForm() {
  const form = document.getElementById("accountRegistrationForm");
  if (!form || form.dataset.bound === "true") return;

  const accountNumberInput = document.getElementById("accountNumber");
  const submitButton = document.getElementById("accountRegistrationBtn");
  form.dataset.bound = "true";

  accountNumberInput?.addEventListener("input", () => {
    accountNumberInput.value = accountNumberInput.value.replace(/[^\d]/g, "");
    setText("accountRegistrationError", "");
    setText("accountRegistrationSuccess", "");
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const accountNumber = String(accountNumberInput?.value || "").trim();
    setText("accountRegistrationError", "");
    setText("accountRegistrationSuccess", "");

    if (!accountNumber) {
      setText("accountRegistrationError", "Vui lòng nhập số tài khoản muốn đăng ký.");
      return;
    }

    if (!/^\d{6,20}$/.test(accountNumber)) {
      setText("accountRegistrationError", "Số tài khoản cần từ 6 đến 20 chữ số.");
      return;
    }

    setButtonState(submitButton, true, "Đang tạo...");

    const createResult = await fetchJson(BANKING_CONFIG.ACCOUNT_CREATE_ENDPOINT, {
      method: "POST",
      body: JSON.stringify({ accountNumber }),
    });

    if (!createResult.ok) {
      const errorMessage = createResult.data?.errors?.accountNumber
        || createResult.data?.data?.errors?.accountNumber
        || createResult.message
        || "Không thể tạo tài khoản lúc này.";
      setText("accountRegistrationError", errorMessage);
      setButtonState(submitButton, false, "Tạo tài khoản");
      return;
    }

    setText(
      "accountRegistrationSuccess",
      createResult.message || "Đăng ký tài khoản thành công. Dashboard đang cập nhật dữ liệu...",
    );
    setButtonState(submitButton, false, "Tạo tài khoản");

    window.setTimeout(() => {
      window.location.reload();
    }, 900);
  });
}

function toggleAccountRegistrationSection(visible) {
  const section = document.getElementById("accountRegistrationSection");
  if (!section) return;
  section.classList.toggle("hidden", !visible);
}

function resolveUserSubtitle(user) {
  const role = String(user?.role || "").toUpperCase();
  if (role === "ADMIN") {
    return "Quản trị viên hệ thống";
  }
  if (user?.province || user?.district) {
    return [user.district, user.province].filter(Boolean).join(", ");
  }
  return "Khách hàng ngân hàng số";
}

function renderAccounts(accounts) {
  const container = document.getElementById("accountsList");
  if (!container) return;

  if (!accounts.length) {
    container.innerHTML = `
      <div class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-6 text-sm text-slate-500">
        Bạn chưa có tài khoản nào. Hãy tạo tài khoản để xem số dư và giao dịch tại dashboard.
      </div>
    `;
    return;
  }

  container.innerHTML = accounts.slice(0, 3).map((account, index) => {
    const primary = index === 0;
    const wrapperClass = primary
      ? "rounded-2xl p-4 bg-gradient-to-r from-indigo-600 to-cyan-500 text-white shadow-lg"
      : "rounded-2xl p-4 bg-slate-50 border border-slate-200";
    const labelClass = primary ? "text-white/80" : "text-gray-500";
    const amountClass = primary ? "text-sm mt-2" : "text-sm text-emerald-600 mt-2";

    return `
      <div class="${wrapperClass}">
        <p class="text-sm ${labelClass}">${escapeHtml(account.name || "Tài khoản")}</p>
        <p class="text-lg font-bold mt-1">${maskAccountNumber(account.accountNumber)}</p>
        <p class="${amountClass}">${formatCurrency(account.balance)}</p>
      </div>
    `;
  }).join("");
}

function renderRecentTransactions(transactions) {
  const container = document.getElementById("recentTransactions");
  if (!container) return;

  if (!transactions.length) {
    container.innerHTML = `
      <div class="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-5 py-8 text-center text-sm text-slate-500">
        Chưa có giao dịch gần đây từ backend để hiển thị.
      </div>
    `;
    return;
  }

  container.innerHTML = transactions.map((item) => {
    const outgoing = normalizeDirection(item.direction) === "OUT";
    const incoming = normalizeDirection(item.direction) === "IN";
    const amountClass = outgoing ? "text-red-500" : incoming ? "text-emerald-500" : "text-slate-700";
    const iconClass = outgoing
      ? "bg-red-100 text-red-500"
      : incoming
        ? "bg-emerald-100 text-emerald-500"
        : "bg-slate-100 text-slate-500";
    const icon = outgoing ? "↗" : incoming ? "↘" : "•";
    const title = buildTransactionTitle(item);

    return `
      <div class="flex items-center justify-between p-4 rounded-2xl bg-slate-50 hover:bg-slate-100 transition">
        <div class="flex items-center gap-4">
          <div class="w-12 h-12 rounded-2xl ${iconClass} flex items-center justify-center text-lg">${icon}</div>
          <div>
            <p class="font-semibold">${escapeHtml(title)}</p>
            <p class="text-sm text-gray-500">${escapeHtml(formatDateTime(item.createdAt))}</p>
          </div>
        </div>
        <p class="font-bold ${amountClass}">${formatSignedAmount(item.amount, item.direction)}</p>
      </div>
    `;
  }).join("");
}

function buildTransactionTitle(item) {
  const account = maskAccountNumber(item.counterpartyAccount);
  const description = String(item.description || "").trim();
  if (description) {
    return description;
  }

  const direction = normalizeDirection(item.direction);
  if (direction === "OUT") {
    return `Chuyển đến ${account}`;
  }
  if (direction === "IN") {
    return `Nhận từ ${account}`;
  }
  return `Giao dịch với ${account}`;
}

function renderNotifications(notifications) {
  const countBadge = document.getElementById("notificationCount");
  const container = document.getElementById("notificationsList");
  if (!countBadge || !container) return;

  countBadge.textContent = `${notifications.length} mới`;
  container.innerHTML = notifications.map((item) => `
    <div class="p-3 rounded-2xl bg-slate-50 hover:bg-slate-100 transition">
      <p class="font-medium">${escapeHtml(item.title)}</p>
      <p class="text-gray-500 mt-1">${escapeHtml(item.subtitle)}</p>
    </div>
  `).join("");
}

function clearAuthData() {
  localStorage.removeItem("token");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("user");
  localStorage.removeItem("authEmail");
}

function bindDashboardReceiverActions() {
  document.getElementById("refreshDashboardReceiversBtn")?.addEventListener("click", async () => {
    await loadDashboardSavedReceivers();
  });
}

async function loadDashboardSavedReceivers() {
  setDashboardReceiverRefreshState(true);
  const result = await fetchJson(BANKING_CONFIG.SAVED_RECEIVERS_ENDPOINT, { method: "GET" });
  if (!result.ok) {
    dashboardState.savedReceivers = [];
    renderDashboardSavedReceivers([]);
    setText("dashboardRecentReceiverCount", "0");
    setText("dashboardRecentReceiverSynced", "0");
    showBanner("dashboardRecentReceiversStatus", result.message || "Không lấy được người nhận gần đây.", "error");
    setDashboardReceiverRefreshState(false);
    return;
  }

  hideBanner("dashboardRecentReceiversStatus");
  const items = Array.isArray(result.data?.data) ? result.data.data : [];
  dashboardState.savedReceivers = items;
  renderDashboardSavedReceivers(items);
  setText("dashboardRecentReceiverCount", String(items.length));
  setText("dashboardRecentReceiverSynced", String(items.length));
  setDashboardReceiverRefreshState(false);
}

function renderDashboardSavedReceivers(items) {
  const container = document.getElementById("dashboardRecentReceivers");
  if (!container) return;

  if (!items.length) {
    container.innerHTML = `
      <div class="lg:col-span-2 rounded-[28px] border border-dashed border-slate-200 bg-white/75 px-5 py-10 text-center text-sm text-slate-500">
        Chưa có người nhận nào từ các giao dịch gần đây để hiển thị trên dashboard.
      </div>
    `;
    return;
  }

  container.innerHTML = items.map((item) => `
    <a
      href="/transfer?accountNumber=${encodeURIComponent(item.accountNumber || "")}"
      class="group block rounded-[28px] border border-slate-200/90 bg-white/90 px-5 py-4 shadow-sm transition hover:-translate-y-0.5 hover:border-cyan-200 hover:bg-white hover:shadow-[0_18px_34px_rgba(14,165,233,0.14)]"
    >
      <p class="truncate text-lg font-black uppercase tracking-[0.02em] text-slate-800 transition group-hover:text-cyan-700">
        ${escapeHtml(item.accountHolderName || "KHONG RO CHU TAI KHOAN")}/${escapeHtml(item.accountNumber || "")}
      </p>
    </a>
  `).join("");
}

function setDashboardReceiverRefreshState(isLoading) {
  const button = document.getElementById("refreshDashboardReceiversBtn");
  const text = document.getElementById("refreshDashboardReceiversText");
  const icon = document.getElementById("refreshDashboardReceiversIcon");
  if (!button || !text || !icon) return;

  button.disabled = isLoading;
  text.textContent = isLoading ? "Đang tải..." : "Làm mới";
  icon.textContent = isLoading ? "⟳" : "↻";
  icon.classList.toggle("animate-spin", isLoading);
}

function validateTransferPayload(payload, primaryAccount, receiverLookup) {
  let valid = true;

  if (!payload.toAccountNumber) {
    setText("receiverAccountError", "Vui lòng nhập số tài khoản người nhận.");
    valid = false;
  } else if (!receiverLookup || receiverLookup.accountNumber !== payload.toAccountNumber) {
    setText("receiverAccountError", "Vui lòng tra cứu và xác nhận đúng tài khoản người nhận.");
    valid = false;
  }

  if (!payload.amount || payload.amount <= 0) {
    setText("amountError", "Vui lòng nhập số tiền hợp lệ.");
    valid = false;
  } else if (primaryAccount && payload.amount > Number(primaryAccount.balance || 0)) {
    setText("amountError", "Số dư không đủ để thực hiện giao dịch.");
    valid = false;
  }

  if (!payload.description) {
    setText("descriptionError", "Vui lòng nhập nội dung chuyển khoản.");
    valid = false;
  }

  return valid;
}

async function lookupReceiverAccount(rawAccountNumber, options = {}) {
  const accountNumber = String(rawAccountNumber || "").trim().replace(/[^\d]/g, "");
  if (!accountNumber) {
    updateSaveReceiverButton("");
    return null;
  }

  clearTransferMessages();
  setText("receiverName", "Đang tra cứu người nhận...");
  updateSaveReceiverButton("");

  const lookupResult = await fetchJson(
    `${BANKING_CONFIG.ACCOUNT_LOOKUP_ENDPOINT}?accountNumber=${encodeURIComponent(accountNumber)}`,
    { method: "GET" },
  );

  if (!lookupResult.ok) {
    setText("receiverAccountError", lookupResult.message || "Không tìm thấy tài khoản người nhận.");
    setText("receiverName", "");
    updateSaveReceiverButton("");
    return null;
  }

  const receiverLookup = extractLookupAccount(lookupResult.data);
  const receiverName = receiverLookup?.accountHolderName || "Không rõ chủ tài khoản";
  setText("receiverName", `Người nhận: ${receiverName}`);
  updateSaveReceiverButton(accountNumber);

  if (typeof options.onSuccess === "function") {
    options.onSuccess(receiverLookup, accountNumber);
  }

  return receiverLookup;
}

function populateTransferBankOptions(select) {
  if (!select) return;
  select.innerHTML = `
    <option value="EBANK" selected>EBank</option>
  `;
  select.disabled = true;
}

function handleTransferApiError(data) {
  const errors = data?.errors || data?.data?.errors || {};
  if (errors.toAccountNumber) setText("receiverAccountError", errors.toAccountNumber);
  if (errors.amount) setText("amountError", errors.amount);
  if (errors.description) setText("descriptionError", errors.description);
}

function clearTransferMessages() {
  ["receiverAccountError", "bankCodeError", "amountError", "descriptionError", "receiverName"].forEach((id) => {
    const element = document.getElementById(id);
    if (element) {
      element.textContent = "";
    }
  });
  updateSaveReceiverButton("");
  hideBanner("transferStatus");
}

async function loadSavedReceiverAccounts(activeInput) {
  const result = await fetchJson(BANKING_CONFIG.SAVED_RECEIVERS_ENDPOINT, { method: "GET" });
  transferPageState.savedReceivers = result.ok && Array.isArray(result.data?.data)
    ? result.data.data
    : [];
  renderSavedReceiverAccounts(activeInput);
}

async function saveReceiverAccount(accountNumber) {
  const normalizedAccountNumber = String(accountNumber || "").trim().replace(/[^\d]/g, "");
  if (!normalizedAccountNumber) {
    return null;
  }

  const result = await fetchJson(BANKING_CONFIG.SAVED_RECEIVERS_ENDPOINT, {
    method: "POST",
    body: JSON.stringify({ accountNumber: normalizedAccountNumber }),
  });

  return result.ok ? result.data?.data || null : null;
}

function renderSavedReceiverAccounts(activeInput) {
  const datalist = document.getElementById("savedReceiverAccounts");
  const list = document.getElementById("savedReceiverAccountsList");
  const wrap = document.getElementById("savedReceiverAccountsWrap");
  if (!datalist || !list || !wrap) return;

  const savedItems = Array.isArray(transferPageState.savedReceivers) ? transferPageState.savedReceivers : [];

  datalist.innerHTML = savedItems.map((item) => `
    <option value="${escapeHtml(item.accountNumber)}">${escapeHtml(item.accountHolderName || "Tài khoản đã lưu")}</option>
  `).join("");

  if (!savedItems.length) {
    list.innerHTML = "";
    wrap.classList.add("hidden");
    return;
  }

  const activeValue = String(activeInput?.value || "").trim();
  list.innerHTML = savedItems.map((item) => {
    const isActive = activeValue === item.accountNumber;
    const buttonClass = isActive
      ? "border-indigo-200 bg-indigo-50 text-indigo-700"
      : "border-gray-200 bg-white text-gray-700 hover:border-indigo-200 hover:bg-indigo-50 hover:text-indigo-700";

    return `
      <button
        type="button"
        data-account-number="${escapeHtml(item.accountNumber)}"
        class="rounded-full border px-3 py-1.5 text-sm font-medium transition ${buttonClass}"
      >
        ${escapeHtml(item.accountNumber)}
      </button>
    `;
  }).join("");
  wrap.classList.remove("hidden");
}

function updateSaveReceiverButton(accountNumber) {
  const button = document.getElementById("saveReceiverBtn");
  if (!button) return;

  const normalizedAccountNumber = String(accountNumber || "").trim().replace(/[^\d]/g, "");

  if (!normalizedAccountNumber) {
    button.classList.add("hidden");
    button.disabled = false;
    button.textContent = "Lưu";
    return;
  }

  const alreadySaved = Array.isArray(transferPageState.savedReceivers)
    && transferPageState.savedReceivers.some((item) => item.accountNumber === normalizedAccountNumber);

  button.classList.remove("hidden");
  button.disabled = alreadySaved;
  button.textContent = alreadySaved ? "Đã lưu" : "Lưu";
}

function showBanner(id, message, variant) {
  const element = document.getElementById(id);
  if (!element) return;

  const variantClasses = {
    success: "bg-emerald-50 text-emerald-700 border border-emerald-100",
    error: "bg-red-50 text-red-700 border border-red-100",
    warning: "bg-amber-50 text-amber-700 border border-amber-100",
  };

  element.className = `mb-4 rounded-2xl px-4 py-3 text-sm ${variantClasses[variant] || variantClasses.warning}`;
  element.textContent = message;
}

function hideBanner(id) {
  const element = document.getElementById(id);
  if (!element) return;
  element.textContent = "";
  element.className = "hidden";
}

function setButtonState(button, isLoading, text) {
  if (!button) return;
  button.disabled = isLoading;
  button.textContent = text;
}

function readStoredUser() {
  try {
    const raw = localStorage.getItem("user");
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

function storeUser(user) {
  localStorage.setItem("user", JSON.stringify(user));
}

function formatCurrency(value) {
  const amount = normalizeAmount(value);
  return `${amount.toLocaleString("vi-VN")}đ`;
}

function formatSignedAmount(value, direction) {
  const amount = normalizeAmount(value);
  const normalizedDirection = normalizeDirection(direction);
  const sign = normalizedDirection === "OUT" ? "-" : normalizedDirection === "IN" ? "+" : "";
  return `${sign}${formatCurrency(amount)}`;
}

function formatDateTime(value) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return "Không rõ thời gian";

  return new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(date);
}

function sumAccountBalances(accounts) {
  return accounts.reduce((total, account) => total + Number(account.balance || 0), 0);
}

function normalizeAmount(value) {
  const amount = Number(value);
  return Number.isFinite(amount) ? amount : 0;
}

function normalizeDirection(direction) {
  return String(direction || "").toUpperCase();
}

function maskAccountNumber(value) {
  const digits = String(value || "").replace(/\s+/g, "");
  return digits ? `•••• ${digits.slice(-4)}` : "•••• 0000";
}

function getInitials(name) {
  return String(name || "")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("") || "EB";
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element && value !== undefined && value !== null) {
    element.textContent = value;
  }
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

async function parseJsonSafe(response) {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    return { message: text || "Phản hồi từ server không hợp lệ." };
  }
}
