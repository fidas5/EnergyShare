package com.energyshare.transaction_service.controller;

import com.energyshare.transaction_service.domain.Transaction;
import com.energyshare.transaction_service.domain.TransactionStatus;
import com.energyshare.transaction_service.dto.CancelRequest;
import com.energyshare.transaction_service.dto.CreateTransactionRequest;
import com.energyshare.transaction_service.dto.HistoryEntryResponse;
import com.energyshare.transaction_service.dto.PageResponse;
import com.energyshare.transaction_service.dto.PaymentRequest;
import com.energyshare.transaction_service.dto.PaymentResponse;
import com.energyshare.transaction_service.dto.ReceiptResponse;
import com.energyshare.transaction_service.dto.TransactionResponse;
import com.energyshare.transaction_service.exception.ApiException;
import com.energyshare.transaction_service.security.AuthenticatedUser;
import com.energyshare.transaction_service.service.TransactionService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Note : AuthenticatedUser est toujours le 1er parametre pour que le 401 passe avant la validation du corps (400). */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionService service;

    @PostMapping
    public ResponseEntity<TransactionResponse> create(AuthenticatedUser user,
                                                      @Valid @RequestBody CreateTransactionRequest request) {
        Transaction tx = service.create(request.reservationId(), user);
        return ResponseEntity.created(URI.create("/api/transactions/" + tx.getId())).body(TransactionResponse.from(tx));
    }

    @GetMapping("/{id}")
    public TransactionResponse get(AuthenticatedUser user, @PathVariable Long id) {
        return TransactionResponse.from(service.get(id, user));
    }

    @GetMapping
    public PageResponse<TransactionResponse> list(AuthenticatedUser user,
                                                  @RequestParam(required = false) TransactionStatus status,
                                                  @RequestParam(required = false) Instant from,
                                                  @RequestParam(required = false) Instant to,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("page doit être >= 0 et size compris entre 1 et " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("'from' doit précéder 'to'");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(service.list(user, status, from, to, pageable), TransactionResponse::from);
    }

    @PostMapping("/{id}/payments")
    public PaymentResponse pay(AuthenticatedUser user, @PathVariable Long id,
                               @Valid @RequestBody PaymentRequest request) {
        return service.pay(id, request, user);
    }

    @PatchMapping("/{id}/complete")
    public TransactionResponse complete(AuthenticatedUser user, @PathVariable Long id) {
        return TransactionResponse.from(service.complete(id, user));
    }

    @PatchMapping("/{id}/cancel")
    public TransactionResponse cancel(AuthenticatedUser user, @PathVariable Long id,
                                      @Valid @RequestBody(required = false) CancelRequest request) {
        return TransactionResponse.from(service.cancel(id, request == null ? null : request.reason(), user));
    }

    @GetMapping("/{id}/receipt")
    public ReceiptResponse receipt(AuthenticatedUser user, @PathVariable Long id) {
        return service.receipt(id, user);
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntryResponse> history(AuthenticatedUser user, @PathVariable Long id) {
        return service.history(id, user).stream().map(HistoryEntryResponse::from).toList();
    }
}
