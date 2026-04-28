(function () {
  const USER_PROFILE_API = `${resolveStatisticsApiBase()}/user/me`;
  const USER_STATISTICS_API = `${resolveStatisticsApiBase()}/reports/my-transactions/statistics`;
  const LOGIN_ROUTE = resolveStatisticsPageRoute("/login");
  const TRANSFER_ROUTE = resolveStatisticsPageRoute("/transfer");

  bootstrap();

  async function bootstrap() {
    const top5Tab = document.getElementById("tab-top5");
    if (!top5Tab) {
      return;
    }

    mountTopRecipientsSkeleton(top5Tab);
    hydrateStatisticsAuthFromQuery();

    if (!getStatisticsToken()) {
      renderTopRecipients([], "Bạn cần đăng nhập lại.");
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
      if (error && error.authExpired) {
        redirectStatisticsLogin();
        return;
      }

      renderTopRecipients([], error && error.message ? error.message : "Không tải được dữ liệu top người nhận.");
    }
  }

  function mountTopRecipientsSkeleton(container) {
    container.innerHTML = [
      '<h2 class="text-2xl font-bold text-slate-800 mb-6">Top 5 Người Nhận Thường Xuyên</h2>',
      '<p id="topRecipientsNote" class="text-sm text-gray-500 mb-4">Đang tải dữ liệu người nhận từ backend.</p>',
      '<div id="topRecipientsList" class="space-y-3"></div>'
    ].join("");
  }

  function renderTopRecipients(recipients, errorMessage) {
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

    recipients.forEach(function (recipient, index) {
      const item = document.createElement("div");
      item.className = "flex items-center justify-between p-4 bg-slate-50 rounded-2xl border border-slate-100 gap-4";

      const accountNumber = String(recipient && recipient.accountNumber ? recipient.accountNumber : "").trim();
      const transferUrl = accountNumber
        ? `${TRANSFER_ROUTE}?accountNumber=${encodeURIComponent(accountNumber)}`
        : TRANSFER_ROUTE;

      item.innerHTML = [
        '<div class="flex items-center gap-4 min-w-0">',
        `  <div class="w-10 h-10 rounded-full bg-amber-100 text-amber-600 flex items-center justify-center font-bold text-lg shrink-0">${index + 1}</div>`,
        '  <div class="min-w-0">',
        `    <p class="font-bold text-slate-800 truncate">${escapeHtml(recipient && recipient.fullName ? recipient.fullName : "Không rõ người nhận")}</p>`,
        `    <p class="text-xs text-gray-500">Đã chuyển ${formatTransferCount(recipient && recipient.transferCount)} lần</p>`,
        "  </div>",
        "</div>",
        ` <a href="${transferUrl}" class="px-4 py-2 bg-indigo-100 text-indigo-700 text-sm font-semibold rounded-xl hover:bg-indigo-200 transition shrink-0">Chuyển ngay</a>`
      ].join("");

      listElement.appendChild(item);
    });
  }

  async function statisticsApiRequest(url) {
    const token = getStatisticsToken();
    if (!token) {
      const missingTokenError = new Error("Bạn cần đăng nhập lại.");
      missingTokenError.authExpired = true;
      throw missingTokenError;
    }

    const response = await fetch(url, {
      method: "GET",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${token}`
      }
    });

    const payload = await parseStatisticsJsonSafe(response);

    if (response.status === 401) {
      clearStatisticsAuth();
      const unauthorizedError = new Error("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
      unauthorizedError.authExpired = true;
      throw unauthorizedError;
    }

    if (!response.ok) {
      throw new Error(payload && (payload.message || payload.error) ? (payload.message || payload.error) : "Yêu cầu tới server không thành công.");
    }

    return payload;
  }

  function renderUserInitials(user) {
    const initialsElement = document.getElementById("userInitials");
    if (!initialsElement) {
      return;
    }

    const fullName = String(user && user.fullName ? user.fullName : "").trim();
    if (!fullName) {
      initialsElement.textContent = "EB";
      return;
    }

    const parts = fullName.split(/\s+/).filter(Boolean);
    initialsElement.textContent = parts.slice(-2).map(function (part) {
      return (part[0] || "").toUpperCase();
    }).join("") || "EB";
  }

  function hydrateStatisticsAuthFromQuery() {
    const url = new URL(window.location.href);
    let shouldCleanUrl = false;

    ["token", "refreshToken", "authEmail"].forEach(function (param) {
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
    ["token", "refreshToken", "authEmail"].forEach(function (param) {
      currentUrl.searchParams.delete(param);
    });
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
    } catch (_error) {
      return { error: text || "Phản hồi từ server không hợp lệ." };
    }
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }
})();
