package com.bankingeconomy.service;

import java.util.Map;

public interface QuarterlyGrowthService {

    String runQuarterlyGrowthJob(String year);

    Map<String, Object> getQuarterlyGrowthResult(String year);
}
