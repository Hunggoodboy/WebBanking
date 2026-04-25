package com.bankingeconomy.service;

import com.bankingeconomy.dto.response.AdminTopTransferTimeResponse;

public interface AdminTopTransferTimeService {
    AdminTopTransferTimeResponse getTopTransferTimeStatistics(String month, boolean rerunJob);
}
