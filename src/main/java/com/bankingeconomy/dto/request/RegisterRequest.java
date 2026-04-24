package com.bankingeconomy.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegisterRequest {

    @NotBlank(message = "Ho ten khong duoc de trong")
    @Size(min = 2, message = "Ho ten phai co it nhat 2 ky tu")
    private String fullName;

    @NotBlank(message = "So dien thoai khong duoc de trong")
    @Pattern(
            regexp = "^(0|\\+84)(\\s|\\.)?((3[2-9])|(5[689])|(7[06-9])|(8[1-689])|(9[0-46-9]))(\\d)(\\s|\\.)?(\\d{3})(\\s|\\.)?(\\d{3})$",
            message = "So dien thoai khong dung dinh dang Viet Nam"
    )
    private String phone;

    @NotBlank(message = "So CMND/CCCD khong duoc de trong")
    @Pattern(regexp = "^\\d{12}$", message = "Can cuoc cong dan phai bao gom 12 chu so")
    private String identityCard;

    @NotBlank(message = "Email khong duoc de trong")
    @Email(message = "Email khong dung dinh dang")
    private String email;

    @NotBlank(message = "Mat khau khong duoc de trong")
    @Size(min = 8, message = "Mat khau phai co it nhat 8 ky tu")
    private String password;

    // Optional for bulk-import payloads such as users_1000.json.
    private String confirmPassword;

    @NotBlank(message = "Vui long nhap tinh thanh")
    private String province;

    @NotBlank(message = "Vui long nhap quan huyen")
    private String district;

    public void setFullName(String fullName) {
        this.fullName = normalizeText(fullName);
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? null : phone.trim();
    }

    public void setIdentityCard(String identityCard) {
        this.identityCard = identityCard == null ? null : identityCard.trim();
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public void setPassword(String password) {
        this.password = password == null ? null : password.trim();
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword == null ? null : confirmPassword.trim();
    }

    public void setProvince(String province) {
        this.province = normalizeText(province);
    }

    public void setDistrict(String district) {
        this.district = normalizeText(district);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        return value.trim().replaceAll("\\s+", " ");
    }
}
