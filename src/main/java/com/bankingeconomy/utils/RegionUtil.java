package com.bankingeconomy.utils;

import com.bankingeconomy.config.database.DbType;
import java.util.Arrays;
import java.util.List;

public class RegionUtil {
    private static final List<String> NORTH_PROVINCES = Arrays.asList(
            "Hà Nội", "Hải Phòng", "Bắc Ninh", "Hà Nam", "Hải Dương",
            "Hưng Yên", "Nam Định", "Ninh Bình", "Thái Bình", "Vĩnh Phúc"
    );

    private static final List<String> MID_PROVINCES = Arrays.asList(
            "Thanh Hóa", "Nghệ An", "Hà Tĩnh", "Quảng Bình", "Quảng Trị",
            "Thừa Thiên Huế", "Đà Nẵng", "Quảng Nam", "Quảng Ngãi", "Bình Định",
            "Phú Yên", "Khánh Hòa", "Ninh Thuận", "Bình Thuận", "Kon Tum",
            "Gia Lai", "Đắk Lắk", "Đắk Nông", "Lâm Đồng"
    );

    public static DbType getRegionByProvince(String province) {
        if (province == null) return DbType.CENTRAL;
        if (NORTH_PROVINCES.contains(province)) return DbType.NORTH;
        if (MID_PROVINCES.contains(province)) return DbType.MID;
        return DbType.SOUTH;
    }
}