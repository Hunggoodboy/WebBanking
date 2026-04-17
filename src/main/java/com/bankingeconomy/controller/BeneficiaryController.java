package com.bankingeconomy.controller;

import com.bankingeconomy.dto.request.BeneficiaryRequest;
import com.bankingeconomy.dto.response.BeneficiaryLookupResponse;
import com.bankingeconomy.dto.response.BeneficiaryResponse;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.service.BeneficiaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/beneficiaries")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @GetMapping
    public ResponseData<List<BeneficiaryResponse>> getMyBeneficiaries(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String keyword
    ) {
        List<BeneficiaryResponse> items = beneficiaryService.getMyBeneficiaries(user, keyword);
        return new ResponseData<>(HttpStatus.OK.value(), "Lấy danh bạ người thụ hưởng thành công", items);
    }

    @GetMapping("/{id}")
    public ResponseData<BeneficiaryResponse> getMyBeneficiary(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        BeneficiaryResponse item = beneficiaryService.getMyBeneficiary(user, id);
        return new ResponseData<>(HttpStatus.OK.value(), "Lấy chi tiết người thụ hưởng thành công", item);
    }

    @GetMapping("/lookup-user")
    public ResponseData<BeneficiaryLookupResponse> lookupUserByUuid(
            @AuthenticationPrincipal User user,
            @RequestParam UUID userId
    ) {
        BeneficiaryLookupResponse item = beneficiaryService.lookupUserByUuid(user, userId);
        return new ResponseData<>(HttpStatus.OK.value(), "Tra cứu người dùng thành công", item);
    }

    @PostMapping
    public ResponseData<BeneficiaryResponse> createBeneficiary(
            @AuthenticationPrincipal User user,
            @RequestBody BeneficiaryRequest request
    ) {
        return beneficiaryService.createBeneficiary(user, request);
    }

    @PutMapping("/{id}")
    public ResponseData<BeneficiaryResponse> updateBeneficiary(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @RequestBody BeneficiaryRequest request
    ) {
        return beneficiaryService.updateBeneficiary(user, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseData<Void> deleteBeneficiary(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id
    ) {
        return beneficiaryService.deleteBeneficiary(user, id);
    }
}
