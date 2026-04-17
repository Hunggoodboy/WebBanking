package com.bankingeconomy.service;

import com.bankingeconomy.dto.request.BeneficiaryRequest;
import com.bankingeconomy.dto.response.BeneficiaryLookupResponse;
import com.bankingeconomy.dto.response.BeneficiaryResponse;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.User;

import java.util.List;
import java.util.UUID;

public interface BeneficiaryService {
    List<BeneficiaryResponse> getMyBeneficiaries(User user, String keyword);

    BeneficiaryResponse getMyBeneficiary(User user, UUID beneficiaryId);

    BeneficiaryLookupResponse lookupUserByUuid(User user, UUID targetUserId);

    ResponseData<BeneficiaryResponse> createBeneficiary(User user, BeneficiaryRequest request);

    ResponseData<BeneficiaryResponse> updateBeneficiary(User user, UUID beneficiaryId, BeneficiaryRequest request);

    ResponseData<Void> deleteBeneficiary(User user, UUID beneficiaryId);
}
