const TOP_TRANSFER_API = `${window.location.origin}/api/reports/admin/top-transfer-time`;
const PROFILE_API = `${window.location.origin}/api/user/me`;

let hourlyChartInstance = null;
let dailyChartInstance = null;

window.addEventListener("DOMContentLoaded", async () => {
  const token = localStorage.getItem("token");
  if (!token) {
    window.location.href = "/login";
    return;
  }

  bindActions();
  setDefaultMonth();
  await loadCurrentUser();
  await loadTopTransferStatistics(false);
});

function bindActions() {
  document.getElementById("viewStatisticsBtn")?.addEventListener("click", () => loadTopTransferStatistics(false));
  document.getElementById("rerunMapReduceBtn")?.addEventListener("click", () => loadTopTransferStatistics(true));
}

function setDefaultMonth() {
  const today = new Date();
  const year = today.getFullYear();
  const month = String(today.getMonth() + 1).padStart(2, "0");
  document.getElementById("monthFilter").value = `${year}-${month}`;
}

async function loadCurrentUser() {
  try {
    const payload = await apiRequest(PROFILE_API);
    const user = payload?.data || payload || {};
    const initials = buildInitials(user.fullName || user.email || "AD");
    document.getElementById("adminUserInitials").textContent = initials;
  } catch (error) {
    setMessage(error.message || "Không lấy được thông tin người dùng.", true);
  }
}

async function loadTopTransferStatistics(rerunJob) {
  const month = document.getElementById("monthFilter")?.value;
  if (!month) {
    setMessage("Vui lòng chọn tháng cần thống kê.", true);
    return;
  }

  setMessage(rerunJob
    ? "Đang chạy lại MapReduce và tải dữ liệu thống kê từ HDFS..."
    : "Đang tải dữ liệu thống kê giờ/ngày giao dịch cao điểm...");

  try {
    const query = new URLSearchParams({ month, rerunJob: String(rerunJob) });
    const payload = await apiRequest(`${TOP_TRANSFER_API}?${query.toString()}`);
    const response = payload?.data || payload;
    renderSummary(response);
    renderCharts(response);
    renderUserTables(response);
    setMessage(`Đã tải thống kê tháng ${response?.month || month} thành công. Nguồn dữ liệu: ${response?.source || "MAPREDUCE"}.`);
  } catch (error) {
    renderSummary(null);
    renderCharts({ hourlyChart: [], dailyChart: [] });
    renderUserTables({ topUsersInPeakHour: [], topUsersInPeakDay: [] });
    setMessage(error.message || "Không tải được dữ liệu thống kê.", true);
  }
}

function renderSummary(response) {
  const peakHour = response?.peakHour || {};
  const peakDay = response?.peakDay || {};
  document.getElementById("peakHourLabel").textContent = peakHour.label || "--";
  document.getElementById("peakHourTransactions").textContent = Number(peakHour.totalTransactions || 0).toLocaleString("vi-VN");
  document.getElementById("peakHourAmount").textContent = formatCurrency(peakHour.totalAmount || 0);

  document.getElementById("peakDayLabel").textContent = peakDay.label || "--";
  document.getElementById("peakDayTransactions").textContent = Number(peakDay.totalTransactions || 0).toLocaleString("vi-VN");
  document.getElementById("peakDayAmount").textContent = formatCurrency(peakDay.totalAmount || 0);

  const source = response?.source || "MAPREDUCE";
  document.getElementById("peakHourSourceLabel").textContent = source;
  document.getElementById("peakDaySourceLabel").textContent = source;
}

function renderCharts(response) {
  const hourlyChart = Array.isArray(response?.hourlyChart) ? response.hourlyChart : [];
  const dailyChart = Array.isArray(response?.dailyChart) ? response.dailyChart : [];

  hourlyChartInstance = renderOrReplaceChart(
    "hourlyChart",
    hourlyChartInstance,
    "bar",
    hourlyChart.map(item => item.label),
    [{ label: "Số giao dịch", data: hourlyChart.map(item => Number(item.totalTransactions || 0)), borderWidth: 1 }],
    { responsive: true, maintainAspectRatio: false, scales: { y: { beginAtZero: true } } }
  );

  dailyChartInstance = renderOrReplaceChart(
    "dailyChart",
    dailyChartInstance,
    "line",
    dailyChart.map(item => item.label),
    [{ label: "Số giao dịch", data: dailyChart.map(item => Number(item.totalTransactions || 0)), tension: 0.25, fill: false, borderWidth: 2 }],
    { responsive: true, maintainAspectRatio: false, scales: { y: { beginAtZero: true } } }
  );
}

function renderUserTables(response) {
  fillUserTable("peakHourUsersTable", response?.topUsersInPeakHour || []);
  fillUserTable("peakDayUsersTable", response?.topUsersInPeakDay || []);
}

function fillUserTable(tableId, rows) {
  const tbody = document.getElementById(tableId);
  if (!tbody) return;

  if (!rows.length) {
    tbody.innerHTML = `
      <tr>
        <td colspan="4" class="py-6 text-center text-slate-500">Không có dữ liệu phù hợp trong tháng đã chọn.</td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = rows.map(row => `
    <tr>
      <td class="py-3 pr-3">
        <div class="font-semibold text-slate-800">${escapeHtml(row.fullName || "Không rõ")}</div>
        <div class="text-xs text-slate-500">${escapeHtml(row.userId || "--")}</div>
      </td>
      <td class="py-3 pr-3 text-slate-600">${escapeHtml(row.accountNumber || "--")}</td>
      <td class="py-3 pr-3 font-semibold text-indigo-700">${Number(row.totalTransactions || 0).toLocaleString("vi-VN")}</td>
      <td class="py-3 text-right font-semibold text-emerald-700">${formatCurrency(row.totalAmount || 0)}</td>
    </tr>
  `).join("");
}

function renderOrReplaceChart(canvasId, existingChart, type, labels, datasets, options) {
  const canvas = document.getElementById(canvasId);
  if (!canvas || typeof Chart === "undefined") return existingChart;
  if (existingChart) existingChart.destroy();
  return new Chart(canvas.getContext("2d"), { type, data: { labels, datasets }, options });
}

async function apiRequest(url, options = {}) {
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

function setMessage(message, isError = false) {
  const element = document.getElementById("topTransferMessage");
  if (!element) return;
  element.textContent = message;
  element.className = `mt-4 rounded-2xl px-4 py-3 text-sm ${isError ? "bg-amber-50 text-amber-700" : "bg-slate-50 text-slate-600"}`;
}

function buildInitials(fullName) {
  return String(fullName || "AD")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map(item => item.charAt(0).toUpperCase())
    .join("") || "AD";
}

function formatCurrency(value) {
  return `${Number(value || 0).toLocaleString("vi-VN")} VND`;
}

function escapeHtml(value) {
  return String(value || "")
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
    return { error: text || "Phản hồi từ server không hợp lệ." };
  }
}
