const BANKING_CONFIG = {
  API_BASE_URL: `${window.location.origin}/api`,
  PROFILE_ENDPOINT: "/auth/me",
  ACCOUNTS_ENDPOINT: "/accounts/me",
  TRANSFER_ENDPOINT: "/v1/transfer/execute",
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
  if (!requireAuth()) return;

  const storedUser = readStoredUser();
  if (storedUser) {
    hydrateUserHeader(storedUser);
  }

  const [profileResult, accountsResult] = await Promise.all([
    fetchJson(BANKING_CONFIG.PROFILE_ENDPOINT),
    fetchJson(BANKING_CONFIG.ACCOUNTS_ENDPOINT),
  ]);

  const user = extractUser(profileResult.data) || storedUser;
  const accounts = extractAccounts(accountsResult.data);
  const primaryAccount = pickPrimaryAccount(accounts);
  const savingsAccount = pickSavingsAccount(accounts);

  if (user) {
    storeUser(user);
    hydrateUserHeader(user);
  }

  renderDashboardSummary(accounts, primaryAccount, savingsAccount);

  if (!accountsResult.ok) {
    showBanner(
      "dashboardStatus",
      "Không lấy được dữ liệu tài khoản từ backend. Trang đang hiển thị dữ liệu mặc định.",
      "warning",
    );
  } else if (!accounts.length) {
    showBanner(
      "dashboardStatus",
      "Bạn chưa có tài khoản ngân hàng nào để hiển thị trên dashboard.",
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

  document.querySelectorAll("[data-quick-amount]").forEach((button) => {
    button.addEventListener("click", () => {
      const current = Number(amountInput.value || 0);
      const increment = Number(button.dataset.quickAmount || 0);
      amountInput.value = String(current + increment);
    });
  });

  receiverAccountInput.addEventListener("input", () => {
    clearTransferMessages();
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearTransferMessages();

    const payload = {
      toAccountNumber: receiverAccountInput.value.trim(),
      amount: Number(amountInput.value),
      description: document.getElementById("transferDescription").value.trim(),
    };

    if (!validateTransferPayload(payload, primaryAccount)) {
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

    if (primaryAccount) {
      primaryAccount.balance = Math.max(0, Number(primaryAccount.balance || 0) - payload.amount);
      setText("transferSourceBalance", formatCurrency(primaryAccount.balance));
    }

    setButtonState(submitBtn, false, "Tiếp tục");
  });
}

function renderDashboardSummary(accounts, primaryAccount, savingsAccount) {
  const totalBalance = sumAccountBalances(accounts);

  setText("heroTotalBalance", formatCurrency(totalBalance));
  setText("heroLastUpdated", `Cập nhật lần cuối: ${formatDateTime(new Date())}`);
  setText("heroPrimaryAccount", maskAccountNumber(primaryAccount?.accountNumber));
  setText("heroSavingsBalance", formatCurrency(savingsAccount?.balance || 0));

  setText("summaryCurrentBalance", formatCurrency(primaryAccount?.balance || 0));
  setText(
    "summaryCurrentBalanceHint",
    `${accounts.length} tài khoản đang hoạt động`,
  );
  setText("summarySentAmount", formatCurrency(0));
  setText("summarySentCount", "0 giao dịch chuyển đi");
  setText("summaryReceivedAmount", formatCurrency(0));
  setText("summaryReceivedCount", "0 giao dịch nhận tiền");
  setText("summaryTransactionCount", "0");
  setText("summaryTransactionHint", "Dữ liệu giao dịch sẽ hiển thị khi backend bổ sung API lịch sử");
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

function requireAuth() {
  const token = localStorage.getItem("token");
  if (token) {
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
  setText("headerUserTier", user.tier || user.customerType || "Khách hàng ngân hàng số");
  setText("userInitials", getInitials(name));
}

function renderAccounts(accounts) {
  const container = document.getElementById("accountsList");
  if (!container || !accounts.length) return;

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

function validateTransferPayload(payload, primaryAccount) {
  let valid = true;

  if (!payload.toAccountNumber) {
    setText("receiverAccountError", "Vui lòng nhập số tài khoản người nhận.");
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
  hideBanner("transferStatus");
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
  const amount = Number(value || 0);
  return `${amount.toLocaleString("vi-VN")}đ`;
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
