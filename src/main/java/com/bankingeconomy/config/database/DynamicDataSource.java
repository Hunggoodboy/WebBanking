package com.bankingeconomy.config.database;

import com.bankingeconomy.config.database.DbContextHolder;
import com.bankingeconomy.config.database.DbType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

@Slf4j
public class DynamicDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        DbType currentDb = DbContextHolder.getCurrentDb();

        // Dòng log này sẽ lật tẩy hệ thống đang thực sự gọi vào DB nào mỗi khi có truy vấn
        log.info("==== 🔍 BỘ ĐỊNH TUYẾN (DATASOURCE) ĐANG CHỌN DB: {} ====",
                currentDb != null ? currentDb : "MẶC ĐỊNH (CENTRAL)");

        return currentDb;
    }
}