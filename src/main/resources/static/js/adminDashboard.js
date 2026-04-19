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
  loadAdminDashboard();
});

function bindAdminDashboardActions() {
  document.getElementById("adminApplyFilterBtn")?.addEventListener("click", loadAdminDashboard);
  document.getElementById("adminResetFilterBtn")?.addEventListener("click", () => {
    applyAdminDefaultRange();
    loadAdminDashboard();
  });
}

function applyAdminDefaultRange() {
  const today = new Date();
  const firstDayOfMonth = new Date(today.getFullYear(), today.getMonth(), 1);
  document.getElementById("adminGroupBy").value = "day";
  document.getElementById("adminStartDate").value = formatDateInput(firstDayOfMonth);
  document.getElementById("adminEndDate").value = formatDateInput(today);
}

async function loadAdminDashboard() {
  const startDate = document.getElementById("adminStartDate")?.value || "";
  const endDate = document.getElementById("adminEndDate")?.value || "";
  const groupBy = document.getElementById("adminGroupBy")?.value || "day";

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
  setElementText("adminTotalTransactions", String(Number(safeSummary.totalTransactions || 0)));
  setElementText("adminTotalAmount", formatCurrency(safeSummary.totalAmount || 0));
  setElementText("adminSuccessCount", String(Number(safeSummary.successCount || 0)));
  setElementText("adminPendingCount", String(Number(safeSummary.pendingCount || 0)));
}

function renderAdminCharts(response) {
  const points = Array.isArray(response?.points) ? response.points : [];
  const statusBreakdown = Array.isArray(response?.statusBreakdown) ? response.statusBreakdown : [];
  const topAccounts = Array.isArray(response?.mapReduceTopAccounts) ? response.mapReduceTopAccounts : [];

  const labels = points.map((item) => item.label);
  const transactionCounts = points.map((item) => Number(item.totalTransactions || 0));
  const amountTotals = points.map((item) => Number(item.totalAmount || 0));

  renderOrReplaceChart("adminTransactionChart", "line", {
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

  renderOrReplaceChart("adminAmountChart", "bar", {
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
