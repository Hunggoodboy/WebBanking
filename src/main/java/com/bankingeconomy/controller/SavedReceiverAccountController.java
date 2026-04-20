package com.bankingeconomy.controller;

import com.bankingeconomy.dto.request.SavedReceiverAccountRequest;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.dto.response.SavedReceiverAccountResponse;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.service.SavedReceiverAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/saved-receivers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SavedReceiverAccountController {

    private final SavedReceiverAccountService savedReceiverAccountService;

    @GetMapping
    public ResponseData<List<SavedReceiverAccountResponse>> getMySavedReceivers(@AuthenticationPrincipal User user) {
        List<SavedReceiverAccountResponse> items = savedReceiverAccountService.getMySavedReceivers(user);
        return new ResponseData<>(HttpStatus.OK.value(), "Lấy danh sách người nhận gần đây thành công", items);
    }

    @PostMapping
    public ResponseData<SavedReceiverAccountResponse> saveReceiver(
            @AuthenticationPrincipal User user,
            @RequestBody SavedReceiverAccountRequest request
    ) {
        return savedReceiverAccountService.saveReceiver(user, request);
    }
}
