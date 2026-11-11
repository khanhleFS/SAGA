package com.microservices.saga.payment.controller;

import com.microservices.saga.common.dto.PaymentRequest;
import com.microservices.saga.common.dto.PaymentResponse;
import com.microservices.saga.payment.model.CustomerWallet;
import com.microservices.saga.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payment")
@Tag(name = "Payment SAGA API", description = "Endpoints for payment processing and SAGA refund compensation")
@CrossOrigin(origins = "*")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/debit")
    @Operation(summary = "SAGA Forward Step: Debit customer wallet")
    public ResponseEntity<PaymentResponse> debit(
            @RequestBody PaymentRequest request,
            @RequestParam(defaultValue = "false") boolean bypassIdempotency) {
        return ResponseEntity.ok(paymentService.debitCustomer(request, bypassIdempotency));
    }

    @PostMapping("/compensate")
    @Operation(summary = "SAGA Compensating Step: Refund customer wallet")
    public ResponseEntity<PaymentResponse> compensateRefund(@RequestBody PaymentRequest request) {
        return ResponseEntity.ok(paymentService.compensateRefund(request));
    }

    @GetMapping
    @Operation(summary = "Get list of all customer wallets")
    public ResponseEntity<List<CustomerWallet>> getAllWallets() {
        return ResponseEntity.ok(paymentService.getAllWallets());
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get wallet for a specific customer")
    public ResponseEntity<CustomerWallet> getWallet(@PathVariable String customerId) {
        CustomerWallet wallet = paymentService.getWallet(customerId);
        return wallet == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(wallet);
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset wallet balances to initial state for demo")
    public ResponseEntity<String> reset() {
        paymentService.initData();
        return ResponseEntity.ok("Customer balances reset to initial demo state");
    }
}
