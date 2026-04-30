const ADMIN_DASHBOARD_API = `${window.location.origin}/api/reports/admin/dashboard-statistics`;
const ADMIN_PROFILE_API = `${window.location.origin}/api/user/me`;

let adminTransactionChart = null;
let adminAmountChart = null;
let adminStatusChart = null;
let adminMapReduceChart = null;

document.addEventListener("DOMContentLoaded", () => {
  if (!localStorage.getItem("token")) {
    window.location.href = "/login";
    return;
  }

  bindAdminDashboardActions();
  applyAdminDefaultRange();
  populateProvinceDensityYears();
  loadAdminDashboard();
});

function populateProvinceDensityYears() {
  const yearSelect = document.getElementById("provinceDensityYear");
  if (!yearSelect) return;
  const currentYear = new Date().getFullYear();
  yearSelect.innerHTML = "";
  for (let y = currentYear; y >= currentYear - 5; y--) {
    const option = document.createElement("option");
    option.value = y;
    option.textContent = y;
    if (y === currentYear) option.selected = true;
    yearSelect.appendChild(option);
  }
}

function bindAdminDashboardActions() {
  document.getElementById("applyFilterBtn")?.addEventListener("click", loadAdminDashboard);
  document.getElementById("resetFilterBtn")?.addEventListener("click", () => {
    applyAdminDefaultRange();
    loadAdminDashboard();
  });
}

function applyAdminDefaultRange() {
  const today = new Date();
  const firstDayOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);
  const groupBy = document.getElementById("filterType");
  if (groupBy) groupBy.value = "day";
  const start = document.getElementById("startValue");
  if (start) start.value = formatDateInput(firstDayOfMonth);
  const end = document.getElementById("endValue");
  if (end) end.value = formatDateInput(today);
}

async function loadAdminDashboard() {
  const startDate = document.getElementById("startValue")?.value || "";
  const endDate = document.getElementById("endValue")?.value || "";
  const groupBy = document.getElementById("filterType")?.value || "day";

  if ((startDate && !endDate) || (!startDate && endDate)) {
    setAdminMessage("Vui lòng nhập đầy đủ ngày bắt đầu và ngày kết thúc.", true);
    return;
  }

  if (startDate && endDate && new Date(`${startDate}T00:00:00`).getTime() > new Date(`${endDate}T23:59:59`).getTime()) {
    setAdminMessage("Ngày bắt đầu không được lớn hơn ngày kết thúc.", true);
    return;
  }

  setAdminMessage("Đang tải dữ liệu dashboard admin từ RESTful API.");

  const query = new URLSearchParams();
  query.set("groupBy", groupBy);
  if (startDate) query.set("start", `${startDate}T00:00:00`);
  if (endDate) query.set("end", `${endDate}T23:59:59`);

  try {
    const [profilePayload, dashboardPayload] = await Promise.all([
      adminApiRequest(ADMIN_PROFILE_API),
      adminApiRequest(`${ADMIN_DASHBOARD_API}?${query.toString()}`),
    ]);

    const user = profilePayload?.data || profilePayload;
    const response = dashboardPayload?.data || dashboardPayload;

    if (String(user?.role || "").toUpperCase() !== "ADMIN") {
      setAdminMessage("Tài khoản hiện tại không có role ADMIN. Trang vẫn hiển thị dữ liệu để bạn kiểm tra giao diện và API.", true);
    } else {
      setAdminMessage(`Đã tải dashboard admin thành công. Nguồn dữ liệu: ${response?.source || "DATABASE"}.`);
    }

    renderAdminSummary(response?.summary);
    renderAdminCharts(response);
  } catch (error) {
    renderAdminSummary(null);
    renderAdminCharts({ points: [], statusBreakdown: [], mapReduceTopAccounts: [] });
    setAdminMessage(error.message || "Không tải được dữ liệu dashboard admin.", true);
  }
}

function renderAdminSummary(summary) {
  const safeSummary = summary || {};
  setElementText("totalCount", String(Number(safeSummary.totalTransactions || 0)));
  setElementText("amountTotal", formatCurrency(safeSummary.totalAmount || 0));
  setElementText("successCount", String(Number(safeSummary.successCount || 0)));
  setElementText("pendingCount", String(Number(safeSummary.pendingCount || 0)));
}

function renderAdminCharts(response) {
  const points = Array.isArray(response?.points) ? response.points : [];
  const statusBreakdown = Array.isArray(response?.statusBreakdown) ? response.statusBreakdown : [];
  const topAccounts = Array.isArray(response?.mapReduceTopAccounts) ? response.mapReduceTopAccounts : [];

  const labels = points.map((item) => item.label);
  const transactionCounts = points.map((item) => Number(item.totalTransactions || 0));
  const amountTotals = points.map((item) => Number(item.totalAmount || 0));

  renderOrReplaceChart("statisticsCountChart", "line", {
    labels,
    datasets: [{
      label: "Số giao dịch",
      data: transactionCounts,
      tension: 0.25,
      fill: false,
      borderWidth: 2,
    }],
  }, {
    responsive: true,
    maintainAspectRatio: false,
    scales: { y: { beginAtZero: true } },
  }, (chart) => { adminTransactionChart = chart; }, adminTransactionChart);

  renderOrReplaceChart("statisticsAmountChart", "bar", {
    labels,
    datasets: [{
      label: "Tổng giá trị giao dịch",
      data: amountTotals,
      borderWidth: 1,
    }],
  }, {
    responsive: true,
    maintainAspectRatio: false,
    scales: { y: { beginAtZero: true } },
  }, (chart) => { adminAmountChart = chart; }, adminAmountChart);

  renderOrReplaceChart("adminStatusChart", "doughnut", {
    labels: statusBreakdown.map((item) => statusLabel(item.label)),
    datasets: [{
      label: "Trạng thái giao dịch",
      data: statusBreakdown.map((item) => Number(item.value || 0)),
      borderWidth: 1,
    }],
  }, {
    responsive: true,
    maintainAspectRatio: false,
  }, (chart) => { adminStatusChart = chart; }, adminStatusChart);

  renderOrReplaceChart("adminMapReduceChart", "bar", {
    labels: topAccounts.map((item) => item.accountNumber || "--"),
    datasets: [{
      label: "Tổng tiền chuyển đi",
      data: topAccounts.map((item) => Number(item.totalAmount || 0)),
      borderWidth: 1,
    }],
  }, {
    responsive: true,
    maintainAspectRatio: false,
    indexAxis: "y",
    scales: { x: { beginAtZero: true } },
  }, (chart) => { adminMapReduceChart = chart; }, adminMapReduceChart);
}

function renderOrReplaceChart(canvasId, type, data, options, assignChart, existingChart) {
  const canvas = document.getElementById(canvasId);
  if (!canvas || typeof Chart === "undefined") {
    return;
  }

  if (existingChart) {
    existingChart.destroy();
  }

  assignChart(new Chart(canvas.getContext("2d"), { type, data, options }));
}

async function adminApiRequest(url, options = {}) {
  const token = localStorage.getItem("token");
  const response = await fetch(url, {
    method: options.method || "GET",
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
      ...(options.headers || {}),
    },
  });

  const payload = await parseJsonSafe(response);
  if (response.status === 401) {
    localStorage.removeItem("token");
    window.location.href = "/login";
    throw new Error("Phiên đăng nhập đã hết hạn.");
  }

  if (!response.ok) {
    throw new Error(payload?.message || payload?.error || "Yêu cầu tới server không thành công.");
  }

  return payload;
}

function setAdminMessage(message, isError = false) {
  const element = document.getElementById("adminDashboardMessage");
  if (!element) return;
  element.textContent = message;
  element.className = `mt-4 rounded-2xl px-4 py-3 text-sm ${isError ? "bg-amber-50 text-amber-700" : "bg-slate-50 text-slate-600"}`;
}

function statusLabel(status) {
  const normalized = String(status || "").toUpperCase();
  if (normalized === "SUCCESS") return "Thành công";
  if (normalized === "PENDING") return "Chờ xử lý";
  if (normalized === "FAILED") return "Thất bại";
  return normalized || "Không rõ";
}

function formatCurrency(value) {
  const amount = Number(value || 0);
  return `${amount.toLocaleString("vi-VN")} VND`;
}

function formatDateInput(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function setElementText(id, value) {
  const element = document.getElementById(id);
  if (element) {
    element.textContent = value;
  }
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

// ================================================================
// MẬT ĐỘ GIAO DỊCH THEO TỈNH
// ================================================================

const PROVINCE_DENSITY_API = `${window.location.origin}/api/hadoop/province-density`;

async function loadProvinceDensity() {
  const yearEl    = document.getElementById("provinceDensityYear");
  const quarterEl = document.getElementById("provinceDensityQuarter");
  const year      = yearEl    ? yearEl.value    : "2026";
  const quarter   = quarterEl ? quarterEl.value : "Q1";

  toggleProvinceDensityState("loading");

  try {
    const token = localStorage.getItem("token");
    if (!token) {
      window.location.href = "/login";
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
      localStorage.removeItem("token");
      window.location.href = "/login";
      return;
    }

    const payload = await parseJsonSafe(response);

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

function renderProvinceDensityTable(data) {
  const tbody = document.getElementById("provinceDensityBody");
  const countEl = document.getElementById("provinceDensityCount");
  if (!tbody) return;

  tbody.innerHTML = "";

  if (countEl) {
    countEl.textContent = data.length.toLocaleString("vi-VN");
  }

  const maxTx = data.length > 0 ? data[0].totalTransactions : 1;

  data.forEach((item, index) => {
    const rank   = item.rank || (index + 1);
    const prov   = escapeHtml(item.province || "Không rõ");
    const total  = item.totalTransactions || 0;
    const pct    = maxTx > 0 ? Math.round((total / maxTx) * 100) : 0;

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

function toggleProvinceDensityState(state, errorMsg = "") {
  const loadingEl  = document.getElementById("provinceDensityLoading");
  const emptyEl    = document.getElementById("provinceDensityEmpty");
  const errorEl    = document.getElementById("provinceDensityError");
  const resultsEl  = document.getElementById("provinceDensityResults");
  const errorMsgEl = document.getElementById("provinceDensityErrorMsg");

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
