const STATISTICS_API_BASE = resolveStatisticsApiBase();
const USER_PROFILE_API = `${STATISTICS_API_BASE}/user/me`;
const USER_STATISTICS_API = `${STATISTICS_API_BASE}/reports/my-transactions/statistics`;
const LOGIN_ROUTE = resolveStatisticsPageRoute("/login");
const TRANSFER_ROUTE = resolveStatisticsPageRoute("/transfer");

document.addEventListener("DOMContentLoaded", async () => {
  hydrateStatisticsAuthFromQuery();

  if (!getStatisticsToken()) {
    redirectStatisticsLogin();
    return;
  }

  try {
    const [profileResponse, statisticsResponse] = await Promise.all([
      statisticsApiRequest(USER_PROFILE_API),
      statisticsApiRequest(`${USER_STATISTICS_API}?groupBy=day`),
    ]);

    renderUserInitials(profileResponse?.data || profileResponse);

    const statisticsPayload = statisticsResponse?.data || statisticsResponse || {};
    renderTopRecipients(statisticsPayload.topRecipients || []);
  } catch (error) {
    if (error?.authExpired) {
      redirectStatisticsLogin();
      return;
    }

    renderTopRecipients([], error.message || "Không tải được dữ liệu top người nhận.");
  }
});

function renderTopRecipients(recipients, errorMessage = "") {
  const noteElement = document.getElementById("topRecipientsNote");
  const listElement = document.getElementById("topRecipientsList");
  if (!noteElement || !listElement) {
    return;
  }

  listElement.innerHTML = "";

  if (errorMessage) {
    noteElement.textContent = errorMessage;
    noteElement.className = "text-sm text-amber-600 mb-4";
    return;
  }

  if (!Array.isArray(recipients) || recipients.length === 0) {
    noteElement.textContent = "Chưa có dữ liệu chuyển khoản thành công để tạo top 5 người nhận.";
    noteElement.className = "text-sm text-gray-500 mb-4";
    return;
  }

  noteElement.textContent = "Xếp hạng dựa trên số lần bạn chuyển khoản thành công tới từng người nhận.";
  noteElement.className = "text-sm text-gray-500 mb-4";

  recipients.forEach((recipient, index) => {
    const item = document.createElement("div");
    item.className = "flex items-center justify-between p-4 bg-slate-50 rounded-2xl border border-slate-100 gap-4";

    const accountNumber = String(recipient?.accountNumber || "").trim();
    const transferUrl = accountNumber
      ? `${TRANSFER_ROUTE}?accountNumber=${encodeURIComponent(accountNumber)}`
      : TRANSFER_ROUTE;

    item.innerHTML = `
      <div class="flex items-center gap-4 min-w-0">
        <div class="w-10 h-10 rounded-full bg-amber-100 text-amber-600 flex items-center justify-center font-bold text-lg shrink-0">${index + 1}</div>
        <div class="min-w-0">
          <p class="font-bold text-slate-800 truncate">${escapeHtml(recipient?.fullName || "Không rõ người nhận")}</p>
          <p class="text-xs text-gray-500">Đã chuyển ${formatTransferCount(recipient?.transferCount)} lần</p>
        </div>
      </div>
      <a href="${transferUrl}" class="px-4 py-2 bg-indigo-100 text-indigo-700 text-sm font-semibold rounded-xl hover:bg-indigo-200 transition shrink-0">Chuyển ngay</a>
    `;

    listElement.appendChild(item);
  });
}

function renderUserInitials(user) {
  const initialsElement = document.getElementById("userInitials");
  if (!initialsElement) {
    return;
  }

  const fullName = String(user?.fullName || "").trim();
  if (!fullName) {
    initialsElement.textContent = "EB";
    return;
  }

  const parts = fullName.split(/\s+/).filter(Boolean);
  initialsElement.textContent = parts.slice(-2).map((part) => part[0]?.toUpperCase() || "").join("") || "EB";
}

async function statisticsApiRequest(url, options = {}) {
  const token = getStatisticsToken();
  if (!token) {
    const error = new Error("Bạn cần đăng nhập lại.");
    error.authExpired = true;
    throw error;
  }

  const response = await fetch(url, {
    method: options.method || "GET",
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...(options.headers || {}),
    },
  });

  const payload = await parseStatisticsJsonSafe(response);

  if (response.status === 401) {
    clearStatisticsAuth();
    const error = new Error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    error.authExpired = true;
    throw error;
  }

  if (!response.ok) {
    throw new Error(payload?.message || payload?.error || "Yêu cầu tới server không thành công.");
  }

  return payload;
}

function hydrateStatisticsAuthFromQuery() {
  const url = new URL(window.location.href);
  let shouldCleanUrl = false;

  ["token", "refreshToken", "authEmail"].forEach((param) => {
    const value = url.searchParams.get(param);
    if (value) {
      localStorage.setItem(param, value);
      url.searchParams.delete(param);
      shouldCleanUrl = true;
    }
  });

  if (shouldCleanUrl) {
    window.history.replaceState({}, "", url.toString());
  }
}

function redirectStatisticsLogin() {
  const loginUrl = new URL(LOGIN_ROUTE, window.location.href);
  const currentUrl = new URL(window.location.href);
  ["token", "refreshToken", "authEmail"].forEach((param) => currentUrl.searchParams.delete(param));
  loginUrl.searchParams.set("redirect", currentUrl.toString());
  window.location.href = loginUrl.toString();
}

function getStatisticsToken() {
  return localStorage.getItem("token");
}

function clearStatisticsAuth() {
  localStorage.removeItem("token");
  localStorage.removeItem("refreshToken");
  localStorage.removeItem("user");
  localStorage.removeItem("authEmail");
}

function resolveStatisticsBackendOrigin() {
  const params = new URLSearchParams(window.location.search);
  const fromQuery = params.get("backend");
  return fromQuery ? fromQuery.replace(/\/+$/, "") : "http://localhost:8084";
}

function resolveStatisticsApiBase() {
  const origin = String(window.location.origin || "");
  if (origin.includes("localhost:8084") || origin.includes("127.0.0.1:8084")) {
    return "/api";
  }

  return `${resolveStatisticsBackendOrigin()}/api`;
}

function resolveStatisticsPageRoute(path) {
  const origin = String(window.location.origin || "");
  if (origin.includes("localhost:8084") || origin.includes("127.0.0.1:8084")) {
    return path;
  }

  return `${resolveStatisticsBackendOrigin()}${path}`;
}

function formatTransferCount(value) {
  return Number(value || 0).toLocaleString("vi-VN");
}

async function parseStatisticsJsonSafe(response) {
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

// ================================================================
// MẬT ĐỘ GIAO DỊCH THEO TỈNH
// ================================================================

const PROVINCE_DENSITY_API = `${STATISTICS_API_BASE}/hadoop/province-density`;

/**
 * Gọi API MapReduce để gộp dữ liệu giao dịch từ các mảnh province
 * và hiển thị bảng xếp hạng mật độ giao dịch từ cao đến thấp.
 */
async function loadProvinceDensity() {
  const yearEl    = document.getElementById("provinceDensityYear");
  const quarterEl = document.getElementById("provinceDensityQuarter");
  const year      = yearEl    ? yearEl.value    : "2026";
  const quarter   = quarterEl ? quarterEl.value : "Q1";

  // Toggle UI states
  toggleProvinceDensityState("loading");

  try {
    const token = getStatisticsToken();
    if (!token) {
      redirectStatisticsLogin();
      return;
    }

    const response = await fetch(
      `${PROVINCE_DENSITY_API}?year=${encodeURIComponent(year)}&quarter=${encodeURIComponent(quarter)}`,
      {
        method: "GET",
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${token}`,
        },
      }
    );

    if (response.status === 401) {
      clearStatisticsAuth();
      redirectStatisticsLogin();
      return;
    }

    const payload = await parseStatisticsJsonSafe(response);

    if (!response.ok) {
      toggleProvinceDensityState("error", payload?.message || "Lỗi không xác định từ server.");
      return;
    }

    const data = payload?.data || [];

    if (!Array.isArray(data) || data.length === 0) {
      toggleProvinceDensityState("empty");
      return;
    }

    renderProvinceDensityTable(data);
    toggleProvinceDensityState("results");
  } catch (error) {
    toggleProvinceDensityState("error", error.message || "Không thể kết nối tới server.");
  }
}

/**
 * Render bảng xếp hạng mật độ giao dịch theo tỉnh.
 * Mỗi dòng có: rank, province, totalTransactions, progress bar tương đối.
 */
function renderProvinceDensityTable(data) {
  const tbody = document.getElementById("provinceDensityBody");
  const countEl = document.getElementById("provinceDensityCount");
  if (!tbody) return;

  tbody.innerHTML = "";

  if (countEl) {
    countEl.textContent = data.length.toLocaleString("vi-VN");
  }

  // Tìm giá trị max để tính tỷ lệ progress bar
  const maxTx = data.length > 0 ? data[0].totalTransactions : 1;

  data.forEach((item, index) => {
    const rank   = item.rank || (index + 1);
    const prov   = escapeHtml(item.province || "Không rõ");
    const total  = item.totalTransactions || 0;
    const pct    = maxTx > 0 ? Math.round((total / maxTx) * 100) : 0;

    // Badge màu cho top 3
    let rankBadge = "";
    if (rank === 1) {
      rankBadge = `<span class="inline-flex items-center justify-center w-8 h-8 rounded-full bg-amber-100 text-amber-700 font-bold text-sm">🥇</span>`;
    } else if (rank === 2) {
      rankBadge = `<span class="inline-flex items-center justify-center w-8 h-8 rounded-full bg-slate-200 text-slate-700 font-bold text-sm">🥈</span>`;
    } else if (rank === 3) {
      rankBadge = `<span class="inline-flex items-center justify-center w-8 h-8 rounded-full bg-orange-100 text-orange-700 font-bold text-sm">🥉</span>`;
    } else {
      rankBadge = `<span class="inline-flex items-center justify-center w-8 h-8 rounded-full bg-slate-100 text-slate-600 font-bold text-sm">${rank}</span>`;
    }

    // Gradient cho progress bar
    let barColor = "from-indigo-400 to-indigo-600";
    if (rank === 1) barColor = "from-amber-400 to-amber-600";
    else if (rank === 2) barColor = "from-slate-400 to-slate-500";
    else if (rank === 3) barColor = "from-orange-400 to-orange-500";

    const row = document.createElement("tr");
    row.className = "border-b border-slate-100 hover:bg-slate-50 transition";
    row.innerHTML = `
      <td class="py-3 px-4">${rankBadge}</td>
      <td class="py-3 px-4 font-medium text-slate-800">${prov}</td>
      <td class="py-3 px-4 text-right font-bold text-indigo-600">${total.toLocaleString("vi-VN")}</td>
      <td class="py-3 px-4">
        <div class="flex items-center gap-2">
          <div class="flex-1 bg-slate-100 rounded-full h-2.5 overflow-hidden">
            <div class="h-full bg-gradient-to-r ${barColor} rounded-full transition-all duration-700" style="width: ${pct}%"></div>
          </div>
          <span class="text-xs text-gray-500 w-10 text-right">${pct}%</span>
        </div>
      </td>
    `;
    tbody.appendChild(row);
  });
}

/**
 * Toggle giữa các trạng thái: loading, empty, error, results.
 */
function toggleProvinceDensityState(state, errorMsg = "") {
  const loadingEl  = document.getElementById("provinceDensityLoading");
  const emptyEl    = document.getElementById("provinceDensityEmpty");
  const errorEl    = document.getElementById("provinceDensityError");
  const resultsEl  = document.getElementById("provinceDensityResults");
  const errorMsgEl = document.getElementById("provinceDensityErrorMsg");

  // Hide all
  [loadingEl, emptyEl, errorEl, resultsEl].forEach(el => {
    if (el) el.classList.add("hidden");
  });

  switch (state) {
    case "loading":
      if (loadingEl) loadingEl.classList.remove("hidden");
      break;
    case "empty":
      if (emptyEl) emptyEl.classList.remove("hidden");
      break;
    case "error":
      if (errorEl) errorEl.classList.remove("hidden");
      if (errorMsgEl) errorMsgEl.textContent = errorMsg;
      break;
    case "results":
      if (resultsEl) resultsEl.classList.remove("hidden");
      break;
  }
}

