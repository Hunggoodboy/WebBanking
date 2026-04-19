const PROFILE_CONFIG = {
  API_BASE_URL: `${window.location.origin}/api`,
  PROFILE_ENDPOINT: "/user/me",
  ACCOUNTS_ENDPOINT: "/accounts/me",
  HISTORY_ROUTE: "/historyTransfer",
  LOGIN_ROUTE: "/login",
};

let profileSnapshot = null;

document.addEventListener("DOMContentLoaded", async () => {
  bindProfileActions();

  if (!requireAuth()) {
    redirectToLogin();
    return;
  }

  await loadProfilePage();
});

function bindProfileActions() {
  document.getElementById("openHistoryBtn")?.addEventListener("click", () => {
    window.location.href = PROFILE_CONFIG.HISTORY_ROUTE;
  });

  document.getElementById("saveProfileBtn")?.addEventListener("click", async () => {
    await saveProfile();
  });

  document.getElementById("resetProfileBtn")?.addEventListener("click", () => {
    if (!profileSnapshot) {
      return;
    }
    fillProfileForm(profileSnapshot);
    clearFieldErrors();
    setHelper("Đã khôi phục dữ liệu hồ sơ hiện có từ backend.");
  });
}

async function loadProfilePage() {
  setHelper("Hệ thống đang tải thông tin khách hàng từ backend.");

  const [profileResult, accountsResult] = await Promise.all([
    fetchJson(PROFILE_CONFIG.PROFILE_ENDPOINT),
    fetchJson(PROFILE_CONFIG.ACCOUNTS_ENDPOINT),
  ]);

  if (!profileResult.ok) {
    setHelper(profileResult.message || "Không tải được hồ sơ khách hàng.", true);
    return;
  }

  const user = extractUser(profileResult.data);
  const accounts = extractAccounts(accountsResult.data);

  if (!user) {
    setHelper("Backend không trả về dữ liệu hồ sơ hợp lệ.", true);
    return;
  }

  profileSnapshot = user;
  storeUser(user);
  renderProfile(user);
  fillProfileForm(user);
  renderAccounts(accounts);

  if (!accountsResult.ok) {
    setHelper("Hồ sơ đã tải xong, nhưng chưa lấy được danh sách tài khoản liên kết.", true);
    return;
  }

  setHelper("Hồ sơ khách hàng đã được đồng bộ thành công.");
}

async function saveProfile() {
  clearFieldErrors();

  const saveButton = document.getElementById("saveProfileBtn");
  const payload = {
    fullName: document.getElementById("fullNameInput")?.value.trim() || "",
    email: document.getElementById("emailInput")?.value.trim() || "",
    phone: document.getElementById("phoneInput")?.value.trim() || "",
    identityCard: document.getElementById("identityCardInput")?.value.trim() || "",
    province: document.getElementById("provinceInput")?.value.trim() || "",
    district: document.getElementById("districtInput")?.value.trim() || "",
    gender: document.getElementById("genderInput")?.value.trim() || "",
  };

  setButtonState(saveButton, true, "Đang lưu...");
  setHelper("Đang gửi thay đổi lên backend...");

  const result = await fetchJson(PROFILE_CONFIG.PROFILE_ENDPOINT, {
    method: "PUT",
    body: JSON.stringify(payload),
  });

  setButtonState(saveButton, false, "Lưu thay đổi");

  if (!result.ok) {
    bindProfileErrors(result.data);
    setHelper(result.message || "Không thể cập nhật hồ sơ khách hàng.", true);
    return;
  }

  const updatedUser = extractUser(result.data);
  if (updatedUser) {
    profileSnapshot = updatedUser;
    storeUser(updatedUser);
    renderProfile(updatedUser);
    fillProfileForm(updatedUser);
  }

  setHelper(result.message || "Cập nhật hồ sơ thành công.");
}

function renderProfile(user) {
  const displayName = user.fullName || "Khách hàng";
  const createdAt = formatDateTime(user.createdAt);

  setText("avatarInitials", getInitials(displayName));
  setText("profileName", displayName);
  setText("profileId", user.id || "--");
  setText("profileEmail", user.email || "--");
  setText("profileCreatedAt", createdAt);
  setText("profilePhone", user.phone || "--");
  setText("profileProvince", user.province || "--");
  setText("profileDistrict", user.district || "--");
  setText("profileRole", formatRole(user.role));

  setText("detailId", user.id || "--");
  setText("detailFullName", displayName);
  setText("detailEmail", user.email || "--");
  setText("detailPhone", user.phone || "--");
  setText("detailIdentityCard", user.identityCard || "--");
  setText("detailGender", user.gender || "Chưa cập nhật");
  setText("detailProvince", user.province || "--");
  setText("detailDistrict", user.district || "--");
  setText("detailRole", formatRole(user.role));
  setText("detailCreatedAt", createdAt);
}

function fillProfileForm(user) {
  setInputValue("fullNameInput", user.fullName);
  setInputValue("emailInput", user.email);
  setInputValue("phoneInput", user.phone);
  setInputValue("identityCardInput", user.identityCard);
  setInputValue("provinceInput", user.province);
  setInputValue("districtInput", user.district);
  setInputValue("genderInput", user.gender);
}

function renderAccounts(accounts) {
  const container = document.getElementById("accountList");
  setText("accountCount", String(accounts.length));
  if (!container) {
    return;
  }

  if (!accounts.length) {
    container.innerHTML = `
      <div class="empty-state">
        Hồ sơ này hiện chưa có tài khoản liên kết nào.
      </div>
    `;
    return;
  }

  container.innerHTML = accounts.map((account) => `
    <div class="account-item">
      <div class="account-main">
        <div class="account-name">${escapeHtml(account.name || "Tài khoản thanh toán")}</div>
        <div class="account-number">${escapeHtml(account.accountNumber || "--")}</div>
        <div class="account-meta">${escapeHtml(account.type || "Không rõ loại tài khoản")}</div>
      </div>
      <div class="account-balance">${formatCurrency(account.balance)}</div>
    </div>
  `).join("");
}

function bindProfileErrors(data) {
  const errors = data?.errors || data?.data?.errors || {};
  const map = {
    fullName: "fullNameError",
    username: "emailError",
    email: "emailError",
    phone: "phoneError",
    customerId: "identityCardError",
    identityCard: "identityCardError",
    province: "provinceError",
    district: "districtError",
    gender: "genderError",
  };

  Object.entries(errors).forEach(([key, value]) => {
    const elementId = map[key];
    if (elementId) {
      setText(elementId, value);
    }
  });
}

function clearFieldErrors() {
  [
    "fullNameError",
    "emailError",
    "phoneError",
    "identityCardError",
    "provinceError",
    "districtError",
    "genderError",
  ].forEach((id) => setText(id, ""));
}

async function fetchJson(endpoint, options = {}) {
  try {
    const response = await fetch(buildApiUrl(endpoint), withAuthHeaders(options));
    const data = await parseJsonSafe(response);
    return {
      ok: response.ok,
      status: response.status,
      data,
      message: data?.message || data?.error,
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
  return `${PROFILE_CONFIG.API_BASE_URL}${endpoint}`;
}

function extractUser(data) {
  return data?.user || data?.data?.user || data?.data || data || null;
}

function extractAccounts(data) {
  const accounts = data?.accounts || data?.data?.accounts || data?.data || data;
  if (!Array.isArray(accounts)) {
    return [];
  }

  return accounts.map((account) => ({
    id: account.id || "",
    accountNumber: account.accountNumber || "",
    balance: Number(account.balance || 0),
    type: account.type || "",
    name: account.name || "Tài khoản",
    status: account.status || "",
  }));
}

function requireAuth() {
  return Boolean(localStorage.getItem("token"));
}

function redirectToLogin() {
  window.location.href = PROFILE_CONFIG.LOGIN_ROUTE;
}

function setHelper(message, isError = false) {
  const helper = document.getElementById("helperText");
  if (!helper) {
    return;
  }
  helper.textContent = message;
  helper.className = `helper${isError ? " error" : " success"}`;
}

function setButtonState(button, isLoading, text) {
  if (!button) {
    return;
  }
  button.disabled = isLoading;
  button.textContent = text;
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element) {
    element.textContent = value ?? "";
  }
}

function setInputValue(id, value) {
  const element = document.getElementById(id);
  if (element) {
    element.value = value ?? "";
  }
}

function storeUser(user) {
  localStorage.setItem("user", JSON.stringify(user));
}

function formatRole(role) {
  const normalized = String(role || "").toUpperCase();
  if (normalized === "ADMIN") {
    return "Quản trị viên";
  }
  if (normalized === "CUSTOMER") {
    return "Khách hàng";
  }
  return role || "--";
}

function formatCurrency(value) {
  const amount = Number(value || 0);
  return `${amount.toLocaleString("vi-VN")}đ`;
}

function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "Chưa có dữ liệu";
  }

  return new Intl.DateTimeFormat("vi-VN", {
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(date);
}

function getInitials(name) {
  return String(name || "")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("") || "KH";
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
