const BACKEND_ORIGIN = resolveBackendOrigin();
const API_BASE = resolveApiBase();
const MY_TRANSACTIONS_API = `${API_BASE}/reports/my-transactions`;
const LOGIN_ROUTE = resolvePageRoute("/login");
const PROFILE_ROUTE = resolvePageRoute("/profile");

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 10;
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const MAX_VISIBLE_PAGE_BUTTONS = 7;
const ALL_TRANSACTIONS_OPTION = "ALL_TRANSACTIONS";

const historyState = {
  page: DEFAULT_PAGE,
  size: DEFAULT_SIZE,
  totalPages: 0,
  totalElements: 0,
  filters: {},
};

document.addEventListener("DOMContentLoaded", async () => {
  bindHistoryActions();
  updateInputMode();
  hydrateAuthFromQuery();

  if (!getToken()) {
    handleMissingAuth();
    return;
  }

  await loadHistory();
});

function bindHistoryActions() {
  document.getElementById("openProfileBtn")?.addEventListener("click", () => {
    window.location.href = PROFILE_ROUTE;
  });

  document.getElementById("accountFilter")?.addEventListener("change", () => {
    loadHistory(historyState.filters, DEFAULT_PAGE);
  });

  document.getElementById("filterType")?.addEventListener("change", updateInputMode);
  document.getElementById("applyFilterBtn")?.addEventListener("click", applyFilters);
  document.getElementById("resetFilterBtn")?.addEventListener("click", resetFilters);

  document.getElementById("pageSizeSelect")?.addEventListener("change", (event) => {
    const nextSize = Number(event.target.value);
    if (!Number.isFinite(nextSize) || nextSize <= 0) {
      return;
    }
    historyState.size = nextSize;
    loadHistory(historyState.filters, DEFAULT_PAGE);
  });

  document.getElementById("prevPageBtn")?.addEventListener("click", () => {
    if (historyState.page > 0) {
      loadHistory(historyState.filters, historyState.page - 1);
    }
  });

  document.getElementById("nextPageBtn")?.addEventListener("click", () => {
    if (historyState.page < historyState.totalPages - 1) {
      loadHistory(historyState.filters, historyState.page + 1);
    }
  });

  document.getElementById("pageNumberList")?.addEventListener("click", (event) => {
    const target = event.target.closest("button[data-page]");
    if (!target) {
      return;
    }

    const nextPage = Number(target.dataset.page);
    if (Number.isInteger(nextPage) && nextPage !== historyState.page) {
      loadHistory(historyState.filters, nextPage);
    }
  });

  populateStaticAccountOption();
  populatePageSizeOptions();
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

function handleMissingAuth() {
  historyState.page = DEFAULT_PAGE;
  historyState.totalPages = 0;
  historyState.totalElements = 0;
  historyState.filters = {};

  renderTransactions([], 0);
  renderPageInfo(0, 0, 0, 0);
  renderPagination();
  setRangeInfo("Bạn chưa đăng nhập. Giao diện lịch sử giao dịch vẫn mở, nhưng cần đăng nhập để tải dữ liệu.", true);
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

function populateStaticAccountOption() {
  const accountFilter = document.getElementById("accountFilter");
  if (!accountFilter) return;

  accountFilter.innerHTML = "";

  const option = document.createElement("option");
  option.value = ALL_TRANSACTIONS_OPTION;
  option.textContent = "Tất cả giao dịch của tôi";
  option.selected = true;
  accountFilter.appendChild(option);
}

function populatePageSizeOptions() {
  const pageSizeSelect = document.getElementById("pageSizeSelect");
  if (!pageSizeSelect) return;

  pageSizeSelect.innerHTML = "";
  PAGE_SIZE_OPTIONS.forEach((size) => {
    const option = document.createElement("option");
    option.value = String(size);
    option.textContent = `${size} dòng`;
    if (size === historyState.size) {
      option.selected = true;
    }
    pageSizeSelect.appendChild(option);
  });
}

async function loadHistory(filters = historyState.filters, page = historyState.page) {
  if (!getToken()) {
    handleMissingAuth();
    return;
  }

  const normalizedFilters = normalizeFilters(filters);
  const normalizedPage = Math.max(Number(page) || 0, 0);

  historyState.filters = normalizedFilters;
  historyState.page = normalizedPage;

  setRangeInfo("Hệ thống đang tải lịch sử giao dịch từ backend.");
  setControlsLoading(true);

  const query = buildHistoryQuery(normalizedFilters, historyState.page, historyState.size);
  const requestUrl = `${MY_TRANSACTIONS_API}?${query.toString()}`;

  try {
    const response = await apiRequest(requestUrl, { method: "GET" });
    const payload = extractTransactionsPayload(response, historyState.size);

    historyState.totalElements = Math.max(Number(payload.totalElements || 0), 0);
    historyState.totalPages = Math.max(Number(payload.totalPages || 0), 0);

    if (historyState.totalPages === 0 && historyState.totalElements > 0) {
      historyState.totalPages = 1;
    }

    historyState.page = clampNumber(
      Number(payload.currentPage || 0),
      0,
      Math.max(historyState.totalPages - 1, 0),
    );

    const visibleTransactions = filterTransactionsByStatus(payload.transactions, normalizedFilters.status);

    renderTransactions(visibleTransactions, historyState.totalElements);
    renderPageInfo(historyState.page, historyState.totalPages, historyState.totalElements, visibleTransactions.length);
    renderPagination();

    setRangeInfo(
      buildRangeInfo(
        normalizedFilters,
        historyState.totalElements,
        visibleTransactions.length,
        historyState.page,
        historyState.totalPages,
      ),
    );
  } catch (error) {
    if (error?.authExpired) {
      handleMissingAuth();
      return;
    }

    historyState.totalPages = 0;
    historyState.totalElements = 0;

    renderTransactions([], 0);
    renderPageInfo(0, 0, 0, 0);
    renderPagination();
    setRangeInfo(error.message || "Không tải được lịch sử giao dịch.", true);
  } finally {
    setControlsLoading(false);
  }
}

function applyFilters() {
  const filterType = document.getElementById("filterType")?.value || "day";
  const startValue = document.getElementById("startValue")?.value.trim() || "";
  const endValue = document.getElementById("endValue")?.value.trim() || "";
  const status = document.getElementById("statusFilter")?.value || "all";

  if ((startValue && !endValue) || (!startValue && endValue)) {
    setRangeInfo("Vui lòng nhập đầy đủ thời gian bắt đầu và kết thúc.", true);
    return;
  }

  if (filterType === "year" && ((startValue && !isValidYear(startValue)) || (endValue && !isValidYear(endValue)))) {
    setRangeInfo("Năm không hợp lệ. Vui lòng nhập năm theo định dạng YYYY.", true);
    return;
  }

  let dateRange = null;
  if (startValue && endValue) {
    const start = normalizeValueByType(startValue, filterType, false);
    const end = normalizeValueByType(endValue, filterType, true);
    if (start > end) {
      setRangeInfo("Thời gian bắt đầu không được lớn hơn thời gian kết thúc.", true);
      return;
    }

    dateRange = buildDateRangeByType(startValue, endValue, filterType);
  }

  const nextFilters = {};
  if (dateRange) {
    nextFilters.start = dateRange.start;
    nextFilters.end = dateRange.end;
  }

  if (status && status !== "all") {
    nextFilters.status = status;
  }

  loadHistory(nextFilters, DEFAULT_PAGE);
}

function resetFilters() {
  const filterType = document.getElementById("filterType");
  const statusFilter = document.getElementById("statusFilter");
  if (filterType) filterType.value = "day";
  if (statusFilter) statusFilter.value = "all";

  updateInputMode();
  loadHistory({}, DEFAULT_PAGE);
}

function renderTransactions(transactions, totalElements) {
  const tableBody = document.getElementById("transactionTableBody");
  const emptyState = document.getElementById("emptyState");

  if (!tableBody || !emptyState) {
    return;
  }

  tableBody.innerHTML = "";
  emptyState.hidden = transactions.length > 0;

  transactions.forEach((item) => {
    const row = document.createElement("tr");
    row.innerHTML = `
      <td><strong>${escapeHtml(item.transactionId || "--")}</strong><span class="muted">Mã định danh giao dịch</span></td>
      <td><strong>${escapeHtml(directionText(item.direction))}</strong><span class="muted">Hướng giao dịch</span></td>
      <td><strong>${escapeHtml(item.counterpartyAccount || "--")}</strong><span class="muted">Tài khoản đối ứng</span></td>
      <td><strong>${escapeHtml(item.description || "--")}</strong><span class="muted">Nội dung giao dịch</span></td>
      <td><strong>${formatSignedAmount(item.amount, item.direction)}</strong><span class="muted">Giá trị giao dịch</span></td>
      <td><span class="status-tag ${statusClass(item.status)}">${statusText(item.status)}</span></td>
      <td><strong>${formatDateTime(item.createdAt)}</strong><span class="muted">Thời điểm phát sinh giao dịch</span></td>
    `;
    tableBody.appendChild(row);
  });

  const totalCount = Number.isFinite(Number(totalElements)) ? Number(totalElements) : transactions.length;
  document.getElementById("totalCount").textContent = String(totalCount);
  document.getElementById("successCount").textContent = String(transactions.filter((item) => normalizeStatus(item.status) === "SUCCESS").length);
  document.getElementById("pendingCount").textContent = String(transactions.filter((item) => normalizeStatus(item.status) === "PENDING").length);
  document.getElementById("amountTotal").textContent = formatCurrency(transactions.reduce((total, item) => total + Number(item.amount || 0), 0));
}

function renderPageInfo(currentPage, totalPages, totalElements, visibleCount) {
  const pageInfo = document.getElementById("pageInfo");
  if (!pageInfo) {
    return;
  }

  const total = Number(totalElements || 0);
  if (!total) {
    pageInfo.textContent = "0 giao dịch";
    return;
  }

  const safeTotalPages = Math.max(Number(totalPages || 0), 1);
  const safePage = clampNumber(Number(currentPage || 0), 0, safeTotalPages - 1);
  pageInfo.textContent = `Trang ${safePage + 1}/${safeTotalPages} - tổng ${total} giao dịch - trang hiện tại ${visibleCount} giao dịch`;
}

function renderPagination() {
  const paginationWrap = document.getElementById("paginationWrap");
  const pageNumberList = document.getElementById("pageNumberList");
  const prevPageBtn = document.getElementById("prevPageBtn");
  const nextPageBtn = document.getElementById("nextPageBtn");

  if (!paginationWrap || !pageNumberList || !prevPageBtn || !nextPageBtn) {
    return;
  }

  const hasData = historyState.totalPages > 0;
  paginationWrap.hidden = !hasData;

  if (!hasData) {
    pageNumberList.innerHTML = "";
    prevPageBtn.disabled = true;
    nextPageBtn.disabled = true;
    return;
  }

  prevPageBtn.disabled = historyState.page <= 0;
  nextPageBtn.disabled = historyState.page >= historyState.totalPages - 1;

  const pageItems = buildPaginationItems(historyState.page + 1, historyState.totalPages);
  pageNumberList.innerHTML = "";

  pageItems.forEach((item) => {
    if (item === "...") {
      const gap = document.createElement("span");
      gap.className = "page-gap";
      gap.textContent = "...";
      pageNumberList.appendChild(gap);
      return;
    }

    const pageIndex = Number(item) - 1;
    const button = document.createElement("button");
    button.type = "button";
    button.className = `page-btn${pageIndex === historyState.page ? " active" : ""}`;
    button.dataset.page = String(pageIndex);
    button.textContent = String(item);
    if (pageIndex === historyState.page) {
      button.disabled = true;
    }
    pageNumberList.appendChild(button);
  });
}

function buildPaginationItems(currentPage, totalPages) {
  if (totalPages <= MAX_VISIBLE_PAGE_BUTTONS) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }

  const items = [1];
  let start = Math.max(2, currentPage - 1);
  let end = Math.min(totalPages - 1, currentPage + 1);

  while (end - start + 1 < MAX_VISIBLE_PAGE_BUTTONS - 2) {
    if (start > 2) {
      start -= 1;
    } else if (end < totalPages - 1) {
      end += 1;
    } else {
      break;
    }
  }

  if (start > 2) {
    items.push("...");
  }

  for (let page = start; page <= end; page += 1) {
    items.push(page);
  }

  if (end < totalPages - 1) {
    items.push("...");
  }

  items.push(totalPages);
  return items;
}

function buildRangeInfo(filters, totalElements, visibleCount, currentPage, totalPages) {
  const scopeText = "Tất cả giao dịch của tôi";
  const hasFilters = filters && (filters.start || filters.end || filters.status);
  const totalText = `${visibleCount}/${totalElements} giao dịch hiển thị`;
  const pageText = totalPages > 0 ? `trang ${currentPage + 1}/${totalPages}` : "chưa có dữ liệu";

  if (!hasFilters) {
    return `${scopeText}: ${totalText}, ${pageText}.`;
  }

  const timeText = filters.start && filters.end
    ? `từ ${formatDisplayDate(filters.start)} đến ${formatDisplayDate(filters.end)}`
    : "theo điều kiện hiện tại";

  const statusTextValue = filters.status ? `, lọc thêm theo trạng thái ${statusText(filters.status).toLowerCase()}` : "";
  return `${scopeText}: đang tra cứu giao dịch ${timeText}${statusTextValue}. ${totalText}, ${pageText}.`;
}

function buildHistoryQuery(filters, page, size) {
  const params = new URLSearchParams();
  params.set("page", String(page));
  params.set("size", String(size));

  if (filters.start) {
    params.set("start", String(filters.start));
  }

  if (filters.end) {
    params.set("end", String(filters.end));
  }

  if (filters.status && filters.status !== "all") {
    params.set("status", String(filters.status));
  }

  return params;
}

function normalizeFilters(filters) {
  const normalized = {};

  if (filters?.start) {
    normalized.start = filters.start;
  }

  if (filters?.end) {
    normalized.end = filters.end;
  }

  if (filters?.status && filters.status !== "all") {
    normalized.status = filters.status;
  }

  return normalized;
}

function extractTransactionsPayload(response, pageSize) {
  const root = response ?? {};
  const data = root.data ?? {};

  const transactions = firstArray(
    data.items,
    root.items,
    data.transactions,
    root.transactions,
    root.content,
    root.results,
  );

  const totalElements = pickNumber([data.totalElements, root.totalElements, root?.page?.totalElements], transactions.length);
  let totalPages = pickNumber([data.totalPages, root.totalPages, root?.page?.totalPages], 0);

  if (!totalPages && totalElements > 0) {
    totalPages = Math.ceil(totalElements / Math.max(Number(pageSize) || DEFAULT_SIZE, 1));
  }

  return {
    transactions,
    currentPage: pickNumber([data.page, root.currentPage, root.pageNumber, root.number], 0),
    totalPages,
    totalElements,
  };
}

function filterTransactionsByStatus(transactions, status) {
  if (!status || status === "all") {
    return transactions;
  }

  const normalized = normalizeStatus(status);
  return transactions.filter((item) => normalizeStatus(item.status) === normalized);
}

function firstArray(...values) {
  const found = values.find((value) => Array.isArray(value));
  return found || [];
}

function pickNumber(values, fallback = 0) {
  for (const value of values) {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return fallback;
}

function isValidYear(value) {
  return /^\d{4}$/.test(value);
}

function buildDateRangeByType(startValue, endValue, type) {
  if (type === "day") {
    return {
      start: `${startValue}T00:00:00`,
      end: `${endValue}T23:59:59`,
    };
  }

  if (type === "week") {
    const [startYear, startWeek] = startValue.split("-W").map(Number);
    const [endYear, endWeek] = endValue.split("-W").map(Number);

    const startDate = getDateOfISOWeek(startWeek, startYear);
    const endDate = getDateOfISOWeek(endWeek, endYear);
    endDate.setDate(endDate.getDate() + 6);
    endDate.setHours(23, 59, 59, 0);

    return {
      start: formatDateTimeLocal(startDate, false),
      end: formatDateTimeLocal(endDate, true),
    };
  }

  if (type === "month") {
    const [startYear, startMonth] = startValue.split("-").map(Number);
    const [endYear, endMonth] = endValue.split("-").map(Number);

    const startDate = new Date(startYear, startMonth - 1, 1, 0, 0, 0, 0);
    const endDate = new Date(endYear, endMonth, 0, 23, 59, 59, 0);

    return {
      start: formatDateTimeLocal(startDate, false),
      end: formatDateTimeLocal(endDate, true),
    };
  }

  return {
    start: `${startValue}-01-01T00:00:00`,
    end: `${endValue}-12-31T23:59:59`,
  };
}

function formatDateTimeLocal(date, isEnd) {
  const normalized = new Date(date);
  if (isEnd) {
    normalized.setHours(23, 59, 59, 0);
  } else {
    normalized.setHours(0, 0, 0, 0);
  }

  const year = normalized.getFullYear();
  const month = String(normalized.getMonth() + 1).padStart(2, "0");
  const day = String(normalized.getDate()).padStart(2, "0");
  const hours = String(normalized.getHours()).padStart(2, "0");
  const minutes = String(normalized.getMinutes()).padStart(2, "0");
  const seconds = String(normalized.getSeconds()).padStart(2, "0");

  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
}

function formatDisplayDate(value) {
  if (!value) return "--";
  return value.replace("T", " ");
}

function setRangeInfo(message, isError = false) {
  const rangeInfo = document.getElementById("rangeInfo");
  if (!rangeInfo) {
    return;
  }

  rangeInfo.textContent = message;
  rangeInfo.className = `meta-box${isError ? " error" : ""}`;
}

function setControlsLoading(isLoading) {
  const applyFilterBtn = document.getElementById("applyFilterBtn");
  const resetFilterBtn = document.getElementById("resetFilterBtn");
  const pageSizeSelect = document.getElementById("pageSizeSelect");
  const prevPageBtn = document.getElementById("prevPageBtn");
  const nextPageBtn = document.getElementById("nextPageBtn");

  if (applyFilterBtn) applyFilterBtn.disabled = isLoading;
  if (resetFilterBtn) resetFilterBtn.disabled = isLoading;
  if (pageSizeSelect) pageSizeSelect.disabled = isLoading;
  if (prevPageBtn) prevPageBtn.disabled = isLoading || historyState.page <= 0;
  if (nextPageBtn) nextPageBtn.disabled = isLoading || historyState.page >= historyState.totalPages - 1;
}

function updateInputMode() {
  const type = document.getElementById("filterType")?.value || "day";
  const startValue = document.getElementById("startValue");
  const endValue = document.getElementById("endValue");

  if (!startValue || !endValue) {
    return;
  }

  startValue.value = "";
  endValue.value = "";
  startValue.placeholder = "";
  endValue.placeholder = "";

  if (type === "day") {
    startValue.type = "date";
    endValue.type = "date";
  }

  if (type === "week") {
    startValue.type = "week";
    endValue.type = "week";
  }

  if (type === "month") {
    startValue.type = "month";
    endValue.type = "month";
  }

  if (type === "year") {
    startValue.type = "number";
    endValue.type = "number";
    startValue.placeholder = "Ví dụ: 2025";
    endValue.placeholder = "Ví dụ: 2026";
  }
}

function normalizeValueByType(value, type, isEnd) {
  if (type === "day") {
    return new Date(`${value}T${isEnd ? "23:59:59" : "00:00:00"}`).getTime();
  }

  if (type === "week") {
    const [year, week] = value.split("-W").map(Number);
    const start = getDateOfISOWeek(week, year);
    const end = new Date(start);
    end.setDate(end.getDate() + 6);
    end.setHours(23, 59, 59, 999);
    return isEnd ? end.getTime() : start.getTime();
  }

  if (type === "month") {
    const [year, month] = value.split("-").map(Number);
    const date = isEnd
      ? new Date(year, month, 0, 23, 59, 59, 999)
      : new Date(year, month - 1, 1, 0, 0, 0, 0);
    return date.getTime();
  }

  const year = Number(value);
  const date = isEnd
    ? new Date(year, 11, 31, 23, 59, 59, 999)
    : new Date(year, 0, 1, 0, 0, 0, 0);
  return date.getTime();
}

function getDateOfISOWeek(week, year) {
  const simple = new Date(year, 0, 1 + (week - 1) * 7);
  const dayOfWeek = simple.getDay();
  const weekStart = new Date(simple);
  if (dayOfWeek <= 4) {
    weekStart.setDate(simple.getDate() - simple.getDay() + 1);
  } else {
    weekStart.setDate(simple.getDate() + 8 - simple.getDay());
  }
  weekStart.setHours(0, 0, 0, 0);
  return weekStart;
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

function normalizeStatus(status) {
  return String(status || "").trim().toUpperCase();
}

function statusText(status) {
  const normalized = normalizeStatus(status);
  if (normalized === "SUCCESS") return "Thành công";
  if (normalized === "PENDING") return "Chờ xử lý";
  if (normalized === "FAILED") return "Thất bại";
  return normalized || "Không rõ";
}

function statusClass(status) {
  const normalized = normalizeStatus(status);
  if (normalized === "SUCCESS") return "status-success";
  if (normalized === "PENDING") return "status-pending";
  return "status-failed";
}

function directionText(direction) {
  const normalized = String(direction || "").trim().toUpperCase();
  if (normalized === "OUT") return "Tiền ra";
  if (normalized === "IN") return "Tiền vào";
  if (normalized === "SELF") return "Nội bộ";
  return "Không xác định";
}

function formatDateTime(value) {
  if (!value) return "--";
  return new Date(value).toLocaleString("vi-VN");
}

function formatCurrency(value) {
  return `${Number(value || 0).toLocaleString("vi-VN")} VND`;
}

function formatSignedAmount(value, direction) {
  const normalizedDirection = String(direction || "").trim().toUpperCase();
  const prefix = normalizedDirection === "OUT" ? "-" : normalizedDirection === "IN" ? "+" : "";
  return `${prefix}${formatCurrency(value)}`;
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

function clampNumber(value, min, max) {
  if (!Number.isFinite(value)) {
    return min;
  }
  return Math.min(Math.max(value, min), max);
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
