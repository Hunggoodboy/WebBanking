const BACKEND_ORIGIN = resolveBackendOrigin();
const API_BASE = resolveApiBase();
const PROFILE_API = `${API_BASE}/profile/me`;
const LOGIN_ROUTE = resolvePageRoute("/login");
const HISTORY_ROUTE = resolvePageRoute("/historyTransfer");

let userState = null;

document.addEventListener("DOMContentLoaded", async () => {
  bindProfileActions();
  hydrateAuthFromQuery();

  if (!getToken()) {
    handleMissingAuth();
    return;
  }

  await fetchProfile();
});

function bindProfileActions() {
  document.getElementById("openHistoryBtn")?.addEventListener("click", () => {
    window.location.href = HISTORY_ROUTE;
  });

  document.getElementById("saveProfileBtn")?.addEventListener("click", saveProfile);
  document.getElementById("resetProfileBtn")?.addEventListener("click", resetProfileForm);
}

async function fetchProfile() {
  setHelper("Hệ thống đang tải thông tin khách hàng từ backend.");

  try {
    const response = await apiRequest(PROFILE_API, { method: "GET" });
    userState = response?.data || null;

    if (!userState) {
      throw new Error("Không nhận được dữ liệu hồ sơ khách hàng.");
    }

    renderProfile(userState);
    setHelper("Thông tin khách hàng đã được đồng bộ từ backend.");
  } catch (error) {
    if (error?.authExpired) {
      handleMissingAuth();
      return;
    }

    setHelper(error.message || "Không tải được thông tin khách hàng.", "error");
  }
}

function renderProfile(profile) {
  const gender = profile.gender || "Chưa cập nhật";
  const role = roleText(profile.role);
  const createdAt = formatDateTime(profile.createdAt);

  setText("avatarInitials", initialsOf(profile.fullName || "KH"));
  setText("profileName", profile.fullName || "Khách hàng");
  setText("profileId", profile.userId || "--");
  setText("profileEmail", profile.email || "--");
  setText("profileCreatedAt", createdAt);
  setText("profilePhone", profile.phone || "--");
  setText("profileGender", gender);
  setText("profileRole", role);
  setText("profileIdentityCard", profile.identityCard || "--");

  setText("detailId", profile.userId || "--");
  setText("detailFullName", profile.fullName || "--");
  setText("detailEmail", profile.email || "--");
  setText("detailPhone", profile.phone || "--");
  setText("detailIdentityCard", profile.identityCard || "--");
  setText("detailGender", gender);
  setText("detailRole", role);
  setText("detailCreatedAt", createdAt);

  document.getElementById("fullNameInput").value = profile.fullName || "";
  document.getElementById("emailInput").value = profile.email || "";
  document.getElementById("phoneInput").value = profile.phone || "";
  document.getElementById("identityCardInput").value = profile.identityCard || "";
  document.getElementById("genderInput").value = normalizeGenderValue(profile.gender);

  renderAccounts(profile.accounts || []);
}

function renderAccounts(accounts) {
  const accountList = document.getElementById("accountList");
  const accountCount = document.getElementById("accountCount");

  if (!accountList || !accountCount) {
    return;
  }

  accountList.innerHTML = "";
  accountCount.textContent = String(accounts.length);

  if (!accounts.length) {
    accountList.innerHTML = '<div class="account-empty">Khách hàng hiện chưa có tài khoản liên kết.</div>';
    return;
  }

  accounts.forEach((account) => {
    const item = document.createElement("div");
    item.className = "account-item";
    item.innerHTML = `
      <span>Số tài khoản</span>
      <strong>${escapeHtml(account.accountNumber || "--")}</strong>
      <span>Số dư hiện tại</span>
      <strong>${formatCurrency(account.balance || 0)}</strong>
      <span>Trạng thái</span>
      <em>${escapeHtml(account.status || "Không xác định")}</em>
    `;
    accountList.appendChild(item);
  });
}

async function saveProfile() {
  if (!userState) {
    setHelper("Bạn cần đăng nhập để tải và cập nhật hồ sơ khách hàng.", "error");
    return;
  }

  const payload = {
    fullName: document.getElementById("fullNameInput").value.trim(),
    email: document.getElementById("emailInput").value.trim(),
    phone: document.getElementById("phoneInput").value.trim(),
    identityCard: document.getElementById("identityCardInput").value.trim(),
    gender: document.getElementById("genderInput").value,
  };

  if (!payload.fullName || !payload.email || !payload.phone || !payload.identityCard) {
    setHelper("Vui lòng nhập đầy đủ họ tên, email, số điện thoại và CMND/CCCD.", "error");
    return;
  }

  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)) {
    setHelper("Email không đúng định dạng.", "error");
    return;
  }

  if (!/^(0|\+84)[0-9]{9,10}$/.test(payload.phone)) {
    setHelper("Số điện thoại không đúng định dạng.", "error");
    return;
  }

  if (!/^\d{12}$/.test(payload.identityCard)) {
    setHelper("CMND/CCCD phải gồm đúng 12 chữ số.", "error");
    return;
  }

  try {
    const response = await apiRequest(PROFILE_API, {
      method: "PUT",
      body: payload,
    });

    userState = response?.data || userState;
    renderProfile(userState);
    setHelper("Thông tin khách hàng đã được cập nhật thành công.", "success");
  } catch (error) {
    if (error?.authExpired) {
      handleMissingAuth();
      return;
    }

    setHelper(error.message || "Cập nhật thông tin khách hàng thất bại.", "error");
  }
}

function resetProfileForm() {
  if (!userState) {
    fetchProfile();
    return;
  }

  renderProfile(userState);
  setHelper("Đã khôi phục thông tin theo dữ liệu đang lưu trên hệ thống.");
}

async function apiRequest(url, options = {}) {
  const token = getToken();
  if (!token) {
    const missingTokenError = new Error("Bạn cần đăng nhập lại.");
    missingTokenError.authExpired = true;
    throw missingTokenError;
  }

  const headers = {
    Accept: "application/json",
    Authorization: `Bearer ${token}`,
    ...(options.headers || {}),
  };

  const requestOptions = {
    method: options.method || "GET",
    headers,
  };

  if (options.body !== undefined && options.body !== null) {
    requestOptions.body = typeof options.body === "string" ? options.body : JSON.stringify(options.body);
    if (!headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }
  }

  const response = await fetch(url, requestOptions);
  const data = await parseJsonSafe(response);

  if (response.status === 401) {
    clearAuthData();
    const unauthorizedError = new Error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    unauthorizedError.authExpired = true;
    throw unauthorizedError;
  }

  if (!response.ok) {
    throw new Error(extractMessage(data) || "Yêu cầu không thành công.");
  }

  return data;
}

function setHelper(message, type = "") {
  const helper = document.getElementById("helperText");
  if (!helper) {
    return;
  }

  helper.textContent = message;
  helper.className = `helper${type ? ` ${type}` : ""}`;
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element) {
    element.textContent = value;
  }
}

function initialsOf(name) {
  return String(name)
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join("");
}

function roleText(role) {
  if (role === "Admin") return "Quản trị viên";
  if (role === "User") return "Khách hàng";
  return "Chưa cập nhật";
}

function normalizeGenderValue(value) {
  if (value === "Nữ") return "Nữ";
  if (value === "Khác") return "Khác";
  return "Nam";
}

function formatDateTime(value) {
  if (!value) return "--";
  return new Date(value).toLocaleString("vi-VN");
}

function formatCurrency(value) {
  return `${Number(value || 0).toLocaleString("vi-VN")} VND`;
}

function getToken() {
  return localStorage.getItem("token");
}

function clearAuthData() {
  localStorage.removeItem("token");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("user");
  localStorage.removeItem("authEmail");
}

function handleMissingAuth() {
  userState = null;
  setHelper("Bạn chưa đăng nhập. Giao diện hồ sơ vẫn mở, nhưng cần đăng nhập để tải dữ liệu.", "error");
  document.getElementById("profileName").textContent = "Chưa đăng nhập";
  document.getElementById("profileEmail").textContent = "--";
  document.getElementById("profileId").textContent = "--";
  document.getElementById("profileCreatedAt").textContent = "--";
  document.getElementById("avatarInitials").textContent = "KH";
  renderAccounts([]);
}

function redirectToLoginPage() {
  const loginUrl = new URL(LOGIN_ROUTE, window.location.href);
  const currentUrl = new URL(window.location.href);

  currentUrl.searchParams.delete("token");
  currentUrl.searchParams.delete("refreshToken");
  currentUrl.searchParams.delete("authEmail");

  loginUrl.searchParams.set("redirect", currentUrl.toString());
  window.location.href = loginUrl.toString();
}

function hydrateAuthFromQuery() {
  const url = new URL(window.location.href);
  const accessToken = url.searchParams.get("token");
  const refreshToken = url.searchParams.get("refreshToken");
  const authEmail = url.searchParams.get("authEmail");

  let shouldCleanUrl = false;

  if (accessToken) {
    localStorage.setItem("token", accessToken);
    shouldCleanUrl = true;
  }

  if (refreshToken) {
    localStorage.setItem("refreshToken", refreshToken);
    shouldCleanUrl = true;
  }

  if (authEmail) {
    localStorage.setItem("authEmail", authEmail);
    shouldCleanUrl = true;
  }

  ["token", "refreshToken", "authEmail"].forEach((param) => {
    if (url.searchParams.has(param)) {
      url.searchParams.delete(param);
      shouldCleanUrl = true;
    }
  });

  if (shouldCleanUrl) {
    window.history.replaceState({}, "", url.toString());
  }
}

function resolveBackendOrigin() {
  const params = new URLSearchParams(window.location.search);
  const fromQuery = params.get("backend");

  if (!fromQuery) {
    return "http://localhost:8084";
  }

  return fromQuery.replace(/\/+$/, "");
}

function resolveApiBase() {
  const origin = String(window.location.origin || "");
  if (origin.includes("localhost:8084") || origin.includes("127.0.0.1:8084")) {
    return "/api";
  }

  return `${BACKEND_ORIGIN}/api`;
}

function resolvePageRoute(path) {
  const origin = String(window.location.origin || "");
  if (origin.includes("localhost:8084") || origin.includes("127.0.0.1:8084")) {
    return path;
  }

  return `${BACKEND_ORIGIN}${path}`;
}

function extractMessage(data) {
  return data?.message || data?.error || "";
}

async function parseJsonSafe(response) {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    return { error: text || "Phản hồi từ server không hợp lệ." };
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
