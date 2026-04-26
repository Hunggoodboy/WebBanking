/**
 * Top 5% VIP Customers — Frontend Logic
 * Fetches from /api/reports/admin/top-vip-customers?year=YYYY
 */

const VIP_API = `${window.location.origin}/api/reports/admin/top-vip-customers`;
let vipBarChart = null;

document.addEventListener("DOMContentLoaded", () => {
  if (!localStorage.getItem("token")) {
    window.location.href = "/login";
    return;
  }

  populateYearFilter();
  bindActions();
  loadVipCustomers();
});

/* ── Year filter ─────────────────────────────────────────── */
function populateYearFilter() {
  const select = document.getElementById("vipYearFilter");
  const currentYear = new Date().getFullYear();
  for (let y = currentYear; y >= currentYear - 5; y--) {
    const opt = document.createElement("option");
    opt.value = y;
    opt.textContent = `Năm ${y}`;
    if (y === currentYear) opt.selected = true;
    select.appendChild(opt);
  }
}

function bindActions() {
  document.getElementById("vipApplyBtn")?.addEventListener("click", loadVipCustomers);
  document.getElementById("vipYearFilter")?.addEventListener("change", loadVipCustomers);
}

/* ── Main data loader ────────────────────────────────────── */
async function loadVipCustomers() {
  const year = document.getElementById("vipYearFilter")?.value || new Date().getFullYear();
  setStatus("Đang tải dữ liệu Top 5% khách hàng VIP năm " + year + "...", false, true);

  try {
    const payload = await apiRequest(`${VIP_API}?year=${year}`);
    const data = payload?.data || payload;
    const customers = Array.isArray(data?.customers) ? data.customers : [];

    if (customers.length === 0) {
      setStatus(`Không tìm thấy khách hàng VIP trong năm ${year}. Có thể chưa đủ dữ liệu giao dịch.`, true);
      renderSummary([], year);
      renderChart([]);
      renderTable([]);
      return;
    }

    setStatus(`Đã tải thành công ${customers.length} khách hàng Top 5% VIP năm ${year}.`);
    renderSummary(customers, year);
    renderChart(customers);
    renderTable(customers);
  } catch (error) {
    setStatus(error.message || "Không thể tải dữ liệu. Vui lòng thử lại.", true);
    renderSummary([], year);
    renderChart([]);
    renderTable([]);
  }
}

/* ── Summary Cards ───────────────────────────────────────── */
function renderSummary(customers, year) {
  const totalAmount = customers.reduce((s, c) => s + Number(c.totalTransferAmount || 0), 0);
  const totalTxn = customers.reduce((s, c) => s + Number(c.totalTransactions || 0), 0);
  const avgAmount = customers.length > 0 ? totalAmount / customers.length : 0;

  setText("statTotalVip", customers.length.toString());
  setText("statTotalAmount", formatCurrency(totalAmount));
  setText("statTotalTxn", totalTxn.toLocaleString("vi-VN"));
  setText("statAvgAmount", formatCurrency(avgAmount));
}

/* ── Chart ───────────────────────────────────────────────── */
function renderChart(customers) {
  const canvas = document.getElementById("vipBarChart");
  if (!canvas || typeof Chart === "undefined") return;

  if (vipBarChart) {
    vipBarChart.destroy();
    vipBarChart = null;
  }

  const top15 = customers.slice(0, 15);
  const labels = top15.map((c, i) => `#${i + 1} ${truncate(c.fullName || c.accountNumber || "N/A", 18)}`);
  const amounts = top15.map(c => Number(c.totalTransferAmount || 0));

  const ctx = canvas.getContext("2d");

  // Create gradient
  const gradient = ctx.createLinearGradient(0, 0, canvas.width, 0);
  gradient.addColorStop(0, "rgba(99, 102, 241, 0.85)");
  gradient.addColorStop(0.5, "rgba(79, 70, 229, 0.75)");
  gradient.addColorStop(1, "rgba(6, 182, 212, 0.85)");

  const hoverGradient = ctx.createLinearGradient(0, 0, canvas.width, 0);
  hoverGradient.addColorStop(0, "rgba(99, 102, 241, 1)");
  hoverGradient.addColorStop(1, "rgba(6, 182, 212, 1)");

  vipBarChart = new Chart(ctx, {
    type: "bar",
    data: {
      labels,
      datasets: [{
        label: "Tổng tiền chuyển (VND)",
        data: amounts,
        backgroundColor: gradient,
        hoverBackgroundColor: hoverGradient,
        borderRadius: 8,
        borderSkipped: false,
        barThickness: 20,
        maxBarThickness: 28,
      }]
    },
    options: {
      indexAxis: "y",
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: "rgba(30, 27, 75, 0.95)",
          titleFont: { size: 13, weight: "bold" },
          bodyFont: { size: 12 },
          padding: 12,
          cornerRadius: 12,
          callbacks: {
            label: (ctx) => ` ${Number(ctx.raw).toLocaleString("vi-VN")} VND`
          }
        }
      },
      scales: {
        x: {
          beginAtZero: true,
          grid: { color: "rgba(0,0,0,0.04)" },
          ticks: {
            font: { size: 11 },
            color: "#94a3b8",
            callback: (val) => {
              if (val >= 1e9) return (val / 1e9).toFixed(1) + "B";
              if (val >= 1e6) return (val / 1e6).toFixed(0) + "M";
              if (val >= 1e3) return (val / 1e3).toFixed(0) + "K";
              return val;
            }
          }
        },
        y: {
          grid: { display: false },
          ticks: { font: { size: 12, weight: "500" }, color: "#475569" }
        }
      }
    }
  });
}

/* ── Table ───────────────────────────────────────────────── */
function renderTable(customers) {
  const tbody = document.getElementById("vipTableBody");
  const emptyState = document.getElementById("vipEmptyState");
  const countEl = document.getElementById("vipTableCount");

  if (!tbody) return;

  if (customers.length === 0) {
    tbody.innerHTML = "";
    emptyState?.classList.remove("hidden");
    if (countEl) countEl.textContent = "0 khách hàng";
    return;
  }

  emptyState?.classList.add("hidden");
  if (countEl) countEl.textContent = `${customers.length} khách hàng`;

  tbody.innerHTML = customers.map((c, i) => {
    const rank = i + 1;
    const badgeClass = rank === 1 ? "rank-gold" : rank === 2 ? "rank-silver" : rank === 3 ? "rank-bronze" : "rank-default";
    const pctRank = Number(c.percentileRank || 0);
    const pctDisplay = (pctRank * 100).toFixed(1);

    return `
      <tr class="vip-row">
        <td class="p-4">
          <div class="w-9 h-9 rounded-xl ${badgeClass} text-white flex items-center justify-center font-bold text-sm shadow-md">
            ${rank}
          </div>
        </td>
        <td class="p-4">
          <div class="font-semibold text-slate-800">${escapeHtml(c.fullName || "—")}</div>
        </td>
        <td class="p-4 text-slate-500">${escapeHtml(c.email || "—")}</td>
        <td class="p-4 text-slate-500">${escapeHtml(c.phone || "—")}</td>
        <td class="p-4">
          <span class="font-mono text-sm bg-slate-100 text-slate-700 px-2 py-1 rounded-lg">${escapeHtml(c.accountNumber || "—")}</span>
        </td>
        <td class="p-4 text-right">
          <span class="font-bold text-emerald-600">${formatCurrency(c.totalTransferAmount || 0)}</span>
        </td>
        <td class="p-4 text-right">
          <span class="font-semibold text-indigo-600">${Number(c.totalTransactions || 0).toLocaleString("vi-VN")}</span>
        </td>
        <td class="p-4 text-right">
          <span class="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold
            ${pctRank >= 0.99 ? 'bg-amber-100 text-amber-700' : 'bg-indigo-100 text-indigo-700'}">
            Top ${(100 - pctRank * 100).toFixed(1)}%
          </span>
        </td>
      </tr>
    `;
  }).join("");
}

/* ── Utilities ───────────────────────────────────────────── */
function setStatus(message, isError = false, isLoading = false) {
  const container = document.getElementById("vipStatusMessage");
  const textEl = document.getElementById("vipStatusText");
  if (!container || !textEl) return;

  textEl.textContent = message;

  const iconSvg = container.querySelector("svg");
  if (iconSvg) {
    if (isLoading) {
      iconSvg.classList.add("pulse-loading");
    } else {
      iconSvg.classList.remove("pulse-loading");
    }
  }

  container.className = `mb-6 rounded-2xl px-5 py-3 text-sm flex items-center gap-3 ${
    isError ? "bg-amber-50 text-amber-700" : "bg-slate-50 text-slate-600"
  }`;
}

async function apiRequest(url) {
  const token = localStorage.getItem("token");
  const response = await fetch(url, {
    method: "GET",
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${token}`,
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

async function parseJsonSafe(response) {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    return { error: text || "Phản hồi từ server không hợp lệ." };
  }
}

function formatCurrency(value) {
  const amount = Number(value || 0);
  return `${amount.toLocaleString("vi-VN")} VND`;
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function truncate(str, maxLen) {
  return str.length > maxLen ? str.substring(0, maxLen) + "…" : str;
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}
