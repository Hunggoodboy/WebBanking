const API_ORIGIN = resolveApiOrigin();
const CONFIG = {
  LOGIN_URL: `${API_ORIGIN}/api/auth/login`,
  REGISTER_URL: `${API_ORIGIN}/api/auth/register`,
  REDIRECT_AFTER_LOGIN: "/dashboard",
  REDIRECT_AFTER_REGISTER: "/login",
};

document.addEventListener("DOMContentLoaded", () => {
  initLoginForm();
  initRegisterForm();
});

function initLoginForm() {
  const form = document.getElementById("loginForm");
  if (!form) return;

  const username = document.getElementById("username");
  const password = document.getElementById("password");
  const usernameError = document.getElementById("usernameError");
  const passwordError = document.getElementById("passwordError");
  const loginBtn = document.getElementById("loginBtn");
  const successMsg = document.getElementById("successMsg");
  const togglePassword = document.getElementById("togglePassword");

  if (togglePassword && password) {
    togglePassword.addEventListener("click", () => {
      togglePasswordVisibility(password, togglePassword);
    });
  }

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    clearLoginErrors(usernameError, passwordError, successMsg);
    // request login
    const payload = {
      email: username.value.trim(),
      password: password.value.trim(),
    };

    const isValid = validateLogin(payload, {
      usernameError,
      passwordError,
    });

    if (!isValid) return;

    setButtonLoading(loginBtn, true, "Đang xử lý...");
    try {
      const response = await fetch(CONFIG.LOGIN_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });
      // response login
      const data = await parseJsonSafe(response);

      if (!response.ok) {
        handleLoginApiError(data, { usernameError, passwordError });
        throw new Error(data?.message || "Đăng nhập thất bại.");
      }

      saveAuthData(data);

      successMsg.textContent = "Đăng nhập thành công.";
      successMsg.style.display = "block";

      if (!document.getElementById("remember")?.checked) {
        // Nếu không muốn ghi nhớ thiết bị thì bạn có thể bỏ phần này.
        // Hiện tại vẫn lưu localStorage theo yêu cầu bài.
      }

      const redirectUrl = resolvePostLoginRedirect(data, payload.email);

      setTimeout(() => {
        window.location.href = redirectUrl;
      }, 500);
    } catch (error) {
      if (!usernameError.textContent && !passwordError.textContent) {
        passwordError.textContent = error.message || "Có lỗi xảy ra khi đăng nhập.";
      }
      console.error("Login error:", error);
    } finally {
      setButtonLoading(loginBtn, false, "Đăng nhập");
    }
  });
}

function initRegisterForm() {
  const form = document.getElementById("registerForm");
  if (!form) return;

  const fullName = document.getElementById("fullName");
  const customerId = document.getElementById("customerId");
  const phone = document.getElementById("phone");
  const username = document.getElementById("username");
  const province = document.getElementById("province");
  const district = document.getElementById("district");
  const password = document.getElementById("password");
  const confirmPassword = document.getElementById("confirmPassword");
  const agree = document.getElementById("agree");

  const fullNameError = document.getElementById("fullNameError");
  const customerIdError = document.getElementById("customerIdError");
  const phoneError = document.getElementById("phoneError");
  const usernameError = document.getElementById("usernameError");
  const provinceError = document.getElementById("provinceError");
  const districtError = document.getElementById("districtError");
  const passwordError = document.getElementById("passwordError");
  const confirmPasswordError = document.getElementById("confirmPasswordError");
  const agreeError = document.getElementById("agreeError");
  const formError = document.getElementById("formError");

  const registerBtn = document.getElementById("registerBtn");
  const successMsg = document.getElementById("successMsg");

  const togglePassword = document.getElementById("togglePassword");
  const toggleConfirmPassword = document.getElementById("toggleConfirmPassword");

  if (togglePassword && password) {
    togglePassword.addEventListener("click", () => {
      togglePasswordVisibility(password, togglePassword);
    });
  }

  if (toggleConfirmPassword && confirmPassword) {
    toggleConfirmPassword.addEventListener("click", () => {
      togglePasswordVisibility(confirmPassword, toggleConfirmPassword);
    });
  }

  bindRegisterFieldEvents([
    { input: fullName, error: fullNameError, normalize: true },
    { input: customerId, error: customerIdError, normalize: true },
    { input: phone, error: phoneError, normalize: true },
    { input: username, error: usernameError, normalize: true },
    { input: province, error: provinceError, normalize: true },
    { input: district, error: districtError, normalize: true },
    { input: password, error: passwordError },
    { input: confirmPassword, error: confirmPasswordError },
    { input: agree, error: agreeError, eventName: "change" },
  ], formError, successMsg);

  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    clearRegisterErrors({
      fullNameError,
      customerIdError,
      phoneError,
      usernameError,
      provinceError,
      districtError,
      passwordError,
      confirmPasswordError,
      agreeError,
      formError,
      successMsg,
    });

    const payload = {
      fullName: normalizeTextInput(fullName.value),
      customerId: customerId.value.trim(),
      phone: phone.value.trim(),
      username: username.value.trim(),
      province: normalizeTextInput(province.value),
      district: normalizeTextInput(district.value),
      password: password.value.trim(),
      confirmPassword: confirmPassword.value.trim(),
      agree: agree.checked,
    };

    const isValid = validateRegister(payload, {
      fullNameError,
      customerIdError,
      phoneError,
      usernameError,
      provinceError,
      districtError,
      passwordError,
      confirmPasswordError,
      agreeError,
    });

    if (!isValid) return;

    setButtonLoading(registerBtn, true, "Đang xử lý...");
    // request register
    try {
      const registerBody = {
        fullName: payload.fullName,
        identityCard: payload.customerId,
        phone: payload.phone,
        email: payload.username,
        province: payload.province,
        district: payload.district,
        password: payload.password,
        confirmPassword: payload.confirmPassword,
      };

      const response = await fetch(CONFIG.REGISTER_URL, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(registerBody),
      });
      // response register
      const data = await parseJsonSafe(response);

      if (!response.ok) {
        handleRegisterApiError(data, {
          fullNameError,
          customerIdError,
          phoneError,
          usernameError,
          provinceError,
          districtError,
          passwordError,
          formError,
        });
        throw new Error(data?.message || "Đăng ký thất bại.");
      }

      const registerSuccessMessage = data?.message || "Đăng ký thành công.";
      successMsg.textContent = registerSuccessMessage;
      successMsg.style.display = "block";

      setTimeout(() => {
        window.location.href = CONFIG.REDIRECT_AFTER_REGISTER;
      }, 1800);
    } catch (error) {
      if (
        !fullNameError.textContent &&
        !customerIdError.textContent &&
        !phoneError.textContent &&
        !usernameError.textContent &&
        !provinceError.textContent &&
        !districtError.textContent &&
        !passwordError.textContent &&
        !formError.textContent
      ) {
        formError.textContent = error.message || "Có lỗi xảy ra khi đăng ký.";
      }
      console.error("Register error:", error);
    } finally {
      setButtonLoading(registerBtn, false, "Đăng ký");
    }
  });
}

function validateLogin(payload, refs) {
  let valid = true;

  if (!payload.email) {
    refs.usernameError.textContent = "Vui lòng nhập email.";
    valid = false;
  } else if (!isValidEmail(payload.email)) {
    refs.usernameError.textContent = "Email không hợp lệ.";
    valid = false;
  }

  if (!payload.password) {
    refs.passwordError.textContent = "Vui lòng nhập mật khẩu.";
    valid = false;
  } else if (payload.password.length < 8) {
    refs.passwordError.textContent = "Mật khẩu phải có ít nhất 8 ký tự.";
    valid = false;
  }

  return valid;
}

function validateRegister(payload, refs) {
  let valid = true;

  if (!payload.fullName) {
    refs.fullNameError.textContent = "Vui lòng nhập họ và tên.";
    valid = false;
  }

  if (!payload.customerId) {
    refs.customerIdError.textContent = "Vui lòng nhập số CMND/CCCD.";
    valid = false;
  } else if (!isValidCustomerId(payload.customerId)) {
    refs.customerIdError.textContent = "CMND/CCCD phải gồm đúng 12 chữ số.";
    valid = false;
  }

  if (!payload.phone) {
    refs.phoneError.textContent = "Vui lòng nhập số điện thoại.";
    valid = false;
  } else if (!isValidPhone(payload.phone)) {
    refs.phoneError.textContent = "Số điện thoại không hợp lệ.";
    valid = false;
  }

  if (!payload.username) {
    refs.usernameError.textContent = "Vui lòng nhập email.";
    valid = false;
  } else if (!isValidEmail(payload.username)) {
    refs.usernameError.textContent = "Email không hợp lệ.";
    valid = false;
  }

  if (!payload.province) {
    refs.provinceError.textContent = "Vui lòng nhập tỉnh/thành phố.";
    valid = false;
  }

  if (!payload.district) {
    refs.districtError.textContent = "Vui lòng nhập quận/huyện.";
    valid = false;
  }

  if (!payload.password) {
    refs.passwordError.textContent = "Vui lòng nhập mật khẩu.";
    valid = false;
  } else if (payload.password.length < 8) {
    refs.passwordError.textContent = "Mật khẩu phải có ít nhất 8 ký tự.";
    valid = false;
  }

  if (!payload.confirmPassword) {
    refs.confirmPasswordError.textContent = "Vui lòng xác nhận mật khẩu.";
    valid = false;
  } else if (payload.confirmPassword !== payload.password) {
    refs.confirmPasswordError.textContent = "Mật khẩu xác nhận không khớp.";
    valid = false;
  }

  if (!payload.agree) {
    refs.agreeError.textContent = "Bạn cần đồng ý với điều khoản sử dụng.";
    valid = false;
  }

  return valid;
}

function clearLoginErrors(usernameError, passwordError, successMsg) {
  usernameError.textContent = "";
  passwordError.textContent = "";
  successMsg.style.display = "none";
}

function clearRegisterErrors(refs) {
  refs.fullNameError.textContent = "";
  refs.customerIdError.textContent = "";
  refs.phoneError.textContent = "";
  refs.usernameError.textContent = "";
  refs.provinceError.textContent = "";
  refs.districtError.textContent = "";
  refs.passwordError.textContent = "";
  refs.confirmPasswordError.textContent = "";
  refs.agreeError.textContent = "";
  refs.formError.textContent = "";
  refs.successMsg.style.display = "none";
}

function bindRegisterFieldEvents(fields, formError, successMsg) {
  for (const field of fields) {
    if (!field.input || !field.error) continue;

    const eventName = field.eventName || "input";
    field.input.addEventListener(eventName, () => {
      field.error.textContent = "";

      if (formError) {
        formError.textContent = "";
      }

      if (successMsg) {
        successMsg.style.display = "none";
      }
    });

    if (eventName !== "change") {
      field.input.addEventListener("blur", () => {
        if (field.normalize && typeof field.input.value === "string") {
          field.input.value = normalizeTextInput(field.input.value);
        }
      });
    }
  }
}

function normalizeTextInput(value) {
  return value.replace(/\s+/g, " ").trim();
}

function togglePasswordVisibility(input, button) {
  const isHidden = input.type === "password";
  input.type = isHidden ? "text" : "password";
  button.textContent = isHidden ? "Ẩn" : "Hiện";
}

function isValidEmail(value) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function isValidPhone(value) {
  return /^(0|\+84)[0-9]{9,10}$/.test(value);
}

function isValidCustomerId(value) {
  return /^[0-9]{12}$/.test(value);
}

function setButtonLoading(button, isLoading, loadingText) {
  if (!button) return;
  button.disabled = isLoading;
  button.textContent = isLoading ? loadingText : button.dataset.defaultText || button.textContent;

  if (!button.dataset.defaultText) {
    button.dataset.defaultText = isLoading ? "" : button.textContent;
  }

  if (!isLoading && button.id === "loginBtn") {
    button.textContent = "Đăng nhập";
  }

  if (!isLoading && button.id === "registerBtn") {
    button.textContent = "Đăng ký";
  }
}
//lưu token vào LocalStorage
function saveAuthData(data) {
  const token = data?.token || data?.accessToken || data?.data?.token || data?.data?.accessToken;
  const refreshToken = data?.refreshToken || data?.data?.refreshToken;
  const user = data?.user || data?.data?.user || data?.data;

  if (token) {
    localStorage.setItem("token", token);
  }

  if (refreshToken) {
    localStorage.setItem("refreshToken", refreshToken);
  }

  if (user) {
    localStorage.setItem("user", JSON.stringify(user));
  }
}

function handleLoginApiError(data, refs) {
  const message = data?.message || data?.error || "";

  if (data?.errors?.username) {
    refs.usernameError.textContent = data.errors.username;
    return;
  }

  if (data?.errors?.password) {
    refs.passwordError.textContent = data.errors.password;
    return;
  }

  if (
    message.toLowerCase().includes("email") ||
    message.toLowerCase().includes("username") ||
    message.toLowerCase().includes("tài khoản")
  ) {
    refs.usernameError.textContent = message;
    return;
  }

  refs.passwordError.textContent = message || "Đăng nhập thất bại.";
}

function handleRegisterApiError(data, refs) {
  const errors = data?.errors || {};
  const message = data?.message || data?.error || "Đăng ký thất bại.";

  if (errors.fullName) refs.fullNameError.textContent = errors.fullName;
  if (errors.customerId) refs.customerIdError.textContent = errors.customerId;
  if (errors.phone) refs.phoneError.textContent = errors.phone;
  if (errors.username) refs.usernameError.textContent = errors.username;
  if (errors.province) refs.provinceError.textContent = errors.province;
  if (errors.district) refs.districtError.textContent = errors.district;
  if (errors.password) refs.passwordError.textContent = errors.password;

  if (Object.keys(errors).length > 0) return;

  const lower = message.toLowerCase();

  if (lower.includes("email")) {
    refs.usernameError.textContent = message;
  } else if (lower.includes("cccd") || lower.includes("cmnd")) {
    refs.customerIdError.textContent = message;
  } else if (lower.includes("điện thoại") || lower.includes("phone")) {
    refs.phoneError.textContent = message;
  } else if (lower.includes("tỉnh") || lower.includes("thành")) {
    refs.provinceError.textContent = message;
  } else if (lower.includes("quận") || lower.includes("huyện")) {
    refs.districtError.textContent = message;
  } else {
    refs.formError.textContent = message;
  }
}

async function parseJsonSafe(response) {
  const text = await response.text();
  try {
    return text ? JSON.parse(text) : {};
  } catch {
    return { message: text || "Phản hồi từ server không hợp lệ." };
  }
}

function resolvePostLoginRedirect(data, email) {
  const fallback = new URL(CONFIG.REDIRECT_AFTER_LOGIN, window.location.origin).toString();
  const params = new URLSearchParams(window.location.search);
  const redirectTarget = params.get("redirect");

  let target;
  try {
    target = redirectTarget ? new URL(redirectTarget) : new URL(fallback);
  } catch {
    target = new URL(fallback);
  }

  if (target.origin !== window.location.origin) {
    const token = data?.token || data?.accessToken || data?.data?.token || data?.data?.accessToken;
    const refreshToken = data?.refreshToken || data?.data?.refreshToken;

    if (token) {
      target.searchParams.set("token", token);
    }

    if (refreshToken) {
      target.searchParams.set("refreshToken", refreshToken);
    }

    if (email) {
      target.searchParams.set("authEmail", email);
    }
  }

  return target.toString();
}

function resolveApiOrigin() {
  const params = new URLSearchParams(window.location.search);
  const backend = params.get("backend");

  if (backend) {
    return backend.replace(/\/+$/, "");
  }

  const hostname = window.location.hostname;
  const port = window.location.port;
  const currentOrigin = window.location.origin;
  const protocol = window.location.protocol;
  const isLocalHost = hostname === "localhost" || hostname === "127.0.0.1";

  if (!currentOrigin || currentOrigin === "null" || protocol === "file:") {
    return "http://localhost:8084";
  }

  if (isLocalHost) {
    if (port === "8084") {
      return currentOrigin;
    }
    return `http://${hostname}:8084`;
  }

  return currentOrigin;
}
