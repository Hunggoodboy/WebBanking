const BENEFICIARY_API_BASE = `${window.location.origin}/api/beneficiaries`;
const BENEFICIARY_LOGIN_ROUTE = "/login";

const beneficiaryState = {
  lookup: null,
  items: [],
  editingId: null,
};

document.addEventListener("DOMContentLoaded", () => {
  if (!requireBeneficiaryAuth()) return;

  bindBeneficiaryActions();
  syncBeneficiaryFormState();
  loadBeneficiaries();
});

function bindBeneficiaryActions() {
  document.getElementById("lookupBeneficiaryForm")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    await lookupUserByUuid();
  });

  document.getElementById("saveBeneficiaryBtn")?.addEventListener("click", async () => {
    await saveBeneficiary();
  });

  document.getElementById("cancelEditBeneficiaryBtn")?.addEventListener("click", () => {
    resetBeneficiaryForm();
  });

  document.getElementById("searchBeneficiaryBtn")?.addEventListener("click", async () => {
    await loadBeneficiaries(document.getElementById("beneficiaryKeyword")?.value || "");
  });

  document.getElementById("beneficiaryKeyword")?.addEventListener("keydown", async (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      await loadBeneficiaries(event.target.value || "");
    }
  });

  document.getElementById("refreshBeneficiariesBtn")?.addEventListener("click", async () => {
    document.getElementById("beneficiaryKeyword").value = "";
    await loadBeneficiaries("");
  });

  document.getElementById("beneficiariesList")?.addEventListener("click", async (event) => {
    const editButton = event.target.closest("button[data-action='edit-beneficiary']");
    if (editButton) {
      startEditBeneficiary(editButton.dataset.id);
      return;
    }

    const button = event.target.closest("button[data-action='delete-beneficiary']");
    if (!button) return;

    await deleteBeneficiary(button.dataset.id);
  });
}

async function lookupUserByUuid() {
  const userId = String(document.getElementById("lookupUserId")?.value || "").trim();
  setText("lookupUserError", "");
  hideBanner("beneficiaryActionStatus");
  toggleLookupPreview(false);
  beneficiaryState.lookup = null;
  syncBeneficiaryFormState();

  if (!userId) {
    setText("lookupUserError", "Vui lòng nhập UUID người dùng cần tra cứu.");
    return;
  }

  const result = await beneficiaryRequest(`/lookup-user?userId=${encodeURIComponent(userId)}`, { method: "GET" });

  if (!result.ok) {
    setText("lookupUserError", result.message || "Không tìm thấy người dùng với UUID đã nhập.");
    return;
  }

  const lookup = result.data?.data || result.data;
  beneficiaryState.lookup = lookup;
  renderLookupPreview(lookup);
  syncBeneficiaryFormState();
}

async function saveBeneficiary() {
  if (!beneficiaryState.lookup?.userId) {
    showBanner(
      "beneficiaryActionStatus",
      beneficiaryState.editingId
        ? "Bạn cần tra cứu UUID người dùng mới trước khi cập nhật."
        : "Bạn cần tra cứu UUID người dùng trước khi thêm vào danh bạ.",
      "warning",
    );
    return;
  }

  const button = document.getElementById("saveBeneficiaryBtn");
  const isEditing = Boolean(beneficiaryState.editingId);
  setButtonState(button, true, isEditing ? "Đang cập nhật..." : "Đang thêm...");
  hideBanner("beneficiaryActionStatus");

  const endpoint = isEditing ? `/${beneficiaryState.editingId}` : "";
  const result = await beneficiaryRequest(endpoint, {
    method: isEditing ? "PUT" : "POST",
    body: JSON.stringify({ targetUserId: beneficiaryState.lookup.userId }),
  });

  if (!result.ok) {
    showBanner(
      "beneficiaryActionStatus",
      result.message || (isEditing ? "Không thể cập nhật người thụ hưởng." : "Không thể thêm người thụ hưởng."),
      "error",
    );
    syncBeneficiaryFormState();
    return;
  }

  showBanner(
    "beneficiaryActionStatus",
    result.message || (isEditing ? "Cập nhật người thụ hưởng thành công." : "Thêm người thụ hưởng thành công."),
    "success",
  );
  resetBeneficiaryForm({ keepStatus: true });
  await loadBeneficiaries(document.getElementById("beneficiaryKeyword")?.value || "");
}

async function loadBeneficiaries(keyword = "") {
  const query = String(keyword || "").trim();
  setBeneficiaryListHint("Đang tải danh bạ từ backend...");

  const endpoint = query ? `?keyword=${encodeURIComponent(query)}` : "";
  const result = await beneficiaryRequest(endpoint, { method: "GET" });

  if (!result.ok) {
    beneficiaryState.items = [];
    renderBeneficiaries([]);
    setBeneficiaryCounts(0, 0);
    setBeneficiaryListHint("Không tải được dữ liệu danh bạ");
    showBanner("beneficiariesStatus", result.message || "Không lấy được danh bạ người thụ hưởng.", "error");
    return;
  }

  hideBanner("beneficiariesStatus");
  const items = Array.isArray(result.data?.data) ? result.data.data : Array.isArray(result.data) ? result.data : [];
  beneficiaryState.items = items;
  renderBeneficiaries(items);
  setBeneficiaryCounts(result.data?.data?.length ?? items.length, items.length);
  setBeneficiaryListHint(
    items.length
      ? `Đã tải ${items.length} người thụ hưởng từ backend`
      : query
        ? "Không tìm thấy người thụ hưởng phù hợp"
        : "Danh bạ hiện chưa có người thụ hưởng nào",
  );
}

async function deleteBeneficiary(id) {
  if (!id) return;

  const result = await beneficiaryRequest(`/${id}`, { method: "DELETE" });
  if (!result.ok) {
    showBanner("beneficiariesStatus", result.message || "Không thể xóa người thụ hưởng.", "error");
    return;
  }

  showBanner("beneficiariesStatus", result.message || "Xóa người thụ hưởng thành công.", "success");
  await loadBeneficiaries(document.getElementById("beneficiaryKeyword")?.value || "");

  if (beneficiaryState.editingId === id) {
    resetBeneficiaryForm();
  } else if (beneficiaryState.lookup?.alreadySaved) {
    await lookupUserByUuid();
  }
}

function startEditBeneficiary(id) {
  const item = beneficiaryState.items.find((entry) => String(entry.id) === String(id));
  if (!item) {
    showBanner("beneficiariesStatus", "Không tìm thấy người thụ hưởng để chỉnh sửa.", "error");
    return;
  }

  beneficiaryState.editingId = item.id;
  beneficiaryState.lookup = {
    userId: item.targetUserId,
    accountId: item.targetAccountId,
    fullName: item.fullName,
    email: item.email,
    phone: item.phone,
    accountNumber: item.accountNumber,
    alreadySaved: false,
  };

  const lookupInput = document.getElementById("lookupUserId");
  if (lookupInput) {
    lookupInput.value = item.targetUserId || "";
  }
  setText("lookupUserError", "");
  hideBanner("beneficiariesStatus");
  renderLookupPreview(beneficiaryState.lookup);
  syncBeneficiaryFormState();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function renderLookupPreview(lookup) {
  toggleLookupPreview(Boolean(lookup));
  if (!lookup) return;

  setText("lookupFullName", lookup.fullName || "Không rõ người dùng");
  setText("lookupAccountNumber", `Số tài khoản: ${lookup.accountNumber || "Chưa có tài khoản"}`);
  setText("lookupUserIdText", lookup.userId || "");
  setText("lookupEmail", lookup.email || "Chưa có email");
  setText("lookupPhone", lookup.phone || "Chưa có số điện thoại");

  const savedBadge = document.getElementById("lookupSavedBadge");
  if (savedBadge) {
    savedBadge.classList.toggle("hidden", !lookup.alreadySaved);
  }
}

function renderBeneficiaries(items) {
  const container = document.getElementById("beneficiariesList");
  if (!container) return;

  if (!items.length) {
    container.innerHTML = `
      <div class="rounded-[28px] border border-dashed border-slate-200 bg-slate-50 px-5 py-10 text-center text-slate-500 lg:col-span-2">
        Chưa có người thụ hưởng nào trong danh bạ hoặc không có kết quả phù hợp với từ khóa tìm kiếm.
      </div>
    `;
    return;
  }

  container.innerHTML = items.map((item) => `
    <article class="rounded-[28px] border border-slate-200 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:shadow-lg">
      <div class="flex items-start justify-between gap-4">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.24em] text-indigo-500">Người thụ hưởng</p>
          <h3 class="mt-2 text-xl font-bold text-slate-900">${escapeHtml(item.fullName || "Không rõ tên")}</h3>
          <p class="mt-1 text-sm text-slate-500">${escapeHtml(item.email || "Chưa có email")}</p>
        </div>
        <div class="flex gap-2">
          <button
            type="button"
            data-action="edit-beneficiary"
            data-id="${escapeHtml(item.id || "")}"
            class="rounded-2xl border border-indigo-200 bg-indigo-50 px-4 py-2 text-sm font-semibold text-indigo-600 transition hover:bg-indigo-100"
          >
            Sửa
          </button>
          <button
            type="button"
            data-action="delete-beneficiary"
            data-id="${escapeHtml(item.id || "")}"
            class="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-2 text-sm font-semibold text-rose-600 transition hover:bg-rose-100"
          >
            Xóa
          </button>
        </div>
      </div>

      <div class="mt-5 grid gap-3 sm:grid-cols-2">
        <div class="rounded-2xl bg-slate-50 p-4">
          <p class="text-xs uppercase tracking-[0.2em] text-slate-400">Số tài khoản</p>
          <p class="mt-2 text-base font-semibold text-slate-800">${escapeHtml(item.accountNumber || "")}</p>
        </div>
        <div class="rounded-2xl bg-slate-50 p-4">
          <p class="text-xs uppercase tracking-[0.2em] text-slate-400">Số điện thoại</p>
          <p class="mt-2 text-base font-semibold text-slate-800">${escapeHtml(item.phone || "Chưa có")}</p>
        </div>
      </div>

      <div class="mt-4 rounded-2xl bg-slate-50 p-4">
        <p class="text-xs uppercase tracking-[0.2em] text-slate-400">UUID người dùng</p>
        <p class="mt-2 break-all text-sm font-semibold text-slate-700">${escapeHtml(item.targetUserId || "")}</p>
      </div>
    </article>
  `).join("");
}

function toggleLookupPreview(visible) {
  document.getElementById("lookupPreview")?.classList.toggle("hidden", !visible);
}

function resetBeneficiaryForm(options = {}) {
  const keepStatus = Boolean(options.keepStatus);
  beneficiaryState.editingId = null;
  beneficiaryState.lookup = null;

  const lookupInput = document.getElementById("lookupUserId");
  if (lookupInput) {
    lookupInput.value = "";
  }

  setText("lookupUserError", "");
  toggleLookupPreview(false);
  if (!keepStatus) {
    hideBanner("beneficiaryActionStatus");
  }
  syncBeneficiaryFormState();
}

function syncBeneficiaryFormState() {
  const isEditing = Boolean(beneficiaryState.editingId);
  const lookup = beneficiaryState.lookup;
  const saveButton = document.getElementById("saveBeneficiaryBtn");
  const cancelButton = document.getElementById("cancelEditBeneficiaryBtn");
  const title = document.getElementById("beneficiaryFormTitle");
  const description = document.getElementById("beneficiaryFormDescription");

  if (title) {
    title.textContent = isEditing ? "Cập nhật người thụ hưởng theo UUID" : "Tra cứu người dùng theo UUID";
  }

  if (description) {
    description.textContent = isEditing
      ? "Bạn đang chỉnh sửa một người thụ hưởng. Nhập UUID người dùng mới, tra cứu lại rồi bấm cập nhật để thay thế."
      : "Nhập đúng UUID của user cần thêm. Hệ thống sẽ lấy tài khoản hoạt động đầu tiên của người đó để lưu vào danh bạ.";
  }

  if (cancelButton) {
    cancelButton.classList.toggle("hidden", !isEditing);
  }

  if (!saveButton) return;

  if (!lookup?.userId) {
    setButtonState(saveButton, true, isEditing ? "Cập nhật danh bạ" : "Thêm vào danh bạ");
    return;
  }

  if (!isEditing && lookup.alreadySaved) {
    setButtonState(saveButton, true, "Đã có trong danh bạ");
    return;
  }

  setButtonState(saveButton, false, isEditing ? "Cập nhật danh bạ" : "Thêm vào danh bạ");
}

function setBeneficiaryCounts(total, filtered) {
  setText("beneficiaryCount", String(total));
  setText("beneficiaryFilteredCount", String(filtered));
}

function setBeneficiaryListHint(message) {
  setText("beneficiaryListHint", message);
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

function setButtonState(button, disabled, text) {
  if (!button) return;
  button.disabled = disabled;
  button.textContent = text;
}

async function beneficiaryRequest(endpoint, options = {}) {
  try {
    const response = await fetch(`${BENEFICIARY_API_BASE}${endpoint}`, withBeneficiaryAuth(options));
    const data = await parseBeneficiaryJson(response);
    return {
      ok: response.ok,
      status: response.status,
      data,
      message: data?.message,
    };
  } catch (error) {
    console.error("Beneficiary API error:", error);
    return {
      ok: false,
      status: 0,
      data: null,
      message: "Không thể kết nối tới backend.",
    };
  }
}

function withBeneficiaryAuth(options = {}) {
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

function requireBeneficiaryAuth() {
  if (localStorage.getItem("token")) {
    return true;
  }

  window.location.href = BENEFICIARY_LOGIN_ROUTE;
  return false;
}

function setText(id, value) {
  const element = document.getElementById(id);
  if (element) {
    element.textContent = value ?? "";
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

async function parseBeneficiaryJson(response) {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    return { message: text || "Phản hồi từ server không hợp lệ." };
  }
}
