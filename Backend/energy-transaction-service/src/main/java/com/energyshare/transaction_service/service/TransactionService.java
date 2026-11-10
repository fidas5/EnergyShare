package com.energyshare.transaction_service.service;

import com.energyshare.transaction_service.client.MarketplaceClient;
import com.energyshare.transaction_service.client.ReservationDto;
import com.energyshare.transaction_service.domain.Payment;
import com.energyshare.transaction_service.domain.PaymentMethod;
import com.energyshare.transaction_service.domain.PaymentStatus;
import com.energyshare.transaction_service.domain.Transaction;
import com.energyshare.transaction_service.domain.TransactionHistory;
import com.energyshare.transaction_service.domain.TransactionStatus;
import com.energyshare.transaction_service.dto.PaymentRequest;
import com.energyshare.transaction_service.dto.PaymentResponse;
import com.energyshare.transaction_service.dto.ReceiptResponse;
import com.energyshare.transaction_service.exception.ApiException;
import com.energyshare.transaction_service.messaging.TransactionEvent;
import com.energyshare.transaction_service.payment.PaymentResult;
import com.energyshare.transaction_service.payment.PaymentSimulator;
import com.energyshare.transaction_service.repository.PaymentRepository;
import com.energyshare.transaction_service.repository.TransactionHistoryRepository;
import com.energyshare.transaction_service.repository.TransactionRepository;
import com.energyshare.transaction_service.security.AuthenticatedUser;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

    static final String RESERVATION_OPEN_STATUS = "RESERVED";

    private final TransactionRepository transactions;
    private final PaymentRepository payments;
    private final TransactionHistoryRepository histories;
    private final MarketplaceClient marketplace;
    private final PaymentSimulator paymentSimulator;
    private final ApplicationEventPublisher events;

    // ------------------------------------------------------------------ POST /api/transactions

    @Transactional
    public Transaction create(Long reservationId, AuthenticatedUser user) {
        ReservationDto reservation = marketplace.getReservation(reservationId);          // 404 / 503

        if (!user.userId().equals(reservation.buyerId())) {
            throw ApiException.forbidden("Seul l'acheteur de la réservation peut créer la transaction");
        }
        if (!RESERVATION_OPEN_STATUS.equalsIgnoreCase(reservation.status())) {
            throw ApiException.conflict("La réservation n'est pas disponible (statut : " + reservation.status() + ")");
        }
        if (transactions.existsByReservationIdAndStatusNot(reservationId, TransactionStatus.CANCELLED)) {
            throw ApiException.conflict("Une transaction existe déjà pour cette réservation");
        }
        if (reservation.energyKwh() == null || reservation.pricePerKwh() == null || reservation.sellerId() == null) {
            throw ApiException.unavailable("Réservation incomplète reçue du Marketplace Service");
        }

        BigDecimal total = reservation.energyKwh().multiply(reservation.pricePerKwh()).setScale(2, RoundingMode.HALF_UP);
        Transaction tx = Transaction.create(reservationId, reservation.buyerId(), reservation.buyerHouseholdId(),
                reservation.sellerId(), reservation.sellerHouseholdId(),
                reservation.energyKwh(), reservation.pricePerKwh(), total);
        Transaction saved = transactions.save(tx);
        recordHistory(saved, null, TransactionStatus.PENDING_PAYMENT, user.userId(), "Transaction créée à partir de la réservation");
        return saved;
    }

    // ------------------------------------------------------------------ GET /api/transactions/{id}

    @Transactional(readOnly = true)
    public Transaction get(Long id, AuthenticatedUser user) {
        Transaction tx = find(id);
        assertCanView(tx, user);
        return tx;
    }

    // ------------------------------------------------------------------ GET /api/transactions

    @Transactional(readOnly = true)
    public Page<Transaction> list(AuthenticatedUser user, TransactionStatus status, Instant from, Instant to, Pageable pageable) {
        if (!user.admin() && user.householdId() == null) {
            throw ApiException.forbidden("Aucun foyer associé à cet utilisateur");
        }
        Specification<Transaction> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!user.admin()) {   // un utilisateur ne voit que les transactions de son foyer
                predicates.add(cb.or(
                        cb.equal(root.get("buyerHouseholdId"), user.householdId()),
                        cb.equal(root.get("sellerHouseholdId"), user.householdId())));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return transactions.findAll(spec, pageable);
    }

    // ------------------------------------------------------------------ POST /api/transactions/{id}/payments

    /** noRollbackFor : un paiement refuse (402) doit rester enregistre avec le statut FAILED. */
    @Transactional(noRollbackFor = ApiException.class)
    public PaymentResponse pay(Long id, PaymentRequest request, AuthenticatedUser user) {
        Transaction tx = find(id);
        if (!tx.isBuyer(user.userId())) {
            throw ApiException.forbidden("Seul l'acheteur peut payer cette transaction");
        }
        if (tx.getStatus() != TransactionStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("La transaction n'est pas en attente de paiement (statut : " + tx.getStatus() + ")");
        }
        if (request.method() == PaymentMethod.CARD && (request.cardToken() == null || request.cardToken().isBlank())) {
            throw ApiException.badRequest("cardToken est obligatoire pour un paiement par carte");
        }

        PaymentResult result = paymentSimulator.processPayment(tx.getTotalAmount(), request.method(), request.cardToken()); // 503 possible

        if (!result.success()) {
            payments.save(Payment.of(tx.getId(), tx.getTotalAmount(), request.method(), PaymentStatus.FAILED, null, result.failureReason()));
            throw ApiException.paymentRequired(result.failureReason());
        }

        Payment payment = payments.save(Payment.of(tx.getId(), tx.getTotalAmount(), request.method(),
                PaymentStatus.SUCCESS, result.reference(), null));
        TransactionStatus previous = tx.getStatus();
        tx.changeStatus(TransactionStatus.PAID);
        transactions.save(tx);
        recordHistory(tx, previous, TransactionStatus.PAID, user.userId(), "Paiement réussi (" + result.reference() + ")");
        events.publishEvent(TransactionEvent.of(TransactionEvent.CONFIRMED, tx));

        return new PaymentResponse(payment.getId(), tx.getId(), payment.getAmount(), payment.getMethod(),
                payment.getStatus(), payment.getReference(), tx.getPaidAt(), tx.getStatus());
    }

    // ------------------------------------------------------------------ PATCH /api/transactions/{id}/complete

    @Transactional
    public Transaction complete(Long id, AuthenticatedUser user) {
        Transaction tx = find(id);
        if (!tx.isSeller(user.userId())) {
            throw ApiException.forbidden("Seul le vendeur peut clôturer l'échange");
        }
        TransactionStatus previous = tx.getStatus();
        tx.changeStatus(TransactionStatus.COMPLETED);                                    // 409 si pas PAID
        transactions.save(tx);
        recordHistory(tx, previous, TransactionStatus.COMPLETED, user.userId(), "Énergie livrée");
        return tx;
    }

    // ------------------------------------------------------------------ PATCH /api/transactions/{id}/cancel

    @Transactional
    public Transaction cancel(Long id, String reason, AuthenticatedUser user) {
        Transaction tx = find(id);
        if (!tx.isBuyer(user.userId()) && !tx.isSeller(user.userId())) {
            throw ApiException.forbidden("Seuls l'acheteur ou le vendeur peuvent annuler la transaction");
        }
        TransactionStatus previous = tx.getStatus();
        tx.changeStatus(TransactionStatus.CANCELLED);                                    // 409 si COMPLETED / CANCELLED
        transactions.save(tx);

        if (previous == TransactionStatus.PAID) {                                        // remboursement simule
            payments.findFirstByTransactionIdAndStatusOrderByCreatedAtDesc(tx.getId(), PaymentStatus.SUCCESS)
                    .ifPresent(p -> {
                        p.setStatus(PaymentStatus.REFUNDED);
                        payments.save(p);
                    });
        }
        recordHistory(tx, previous, TransactionStatus.CANCELLED, user.userId(),
                (reason == null || reason.isBlank()) ? "Transaction annulée" : reason);
        events.publishEvent(TransactionEvent.of(TransactionEvent.CANCELLED, tx));
        return tx;
    }

    // ------------------------------------------------------------------ GET /api/transactions/{id}/receipt

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(Long id, AuthenticatedUser user) {
        Transaction tx = find(id);
        assertCanView(tx, user);
        if (tx.getStatus() != TransactionStatus.COMPLETED) {
            throw ApiException.conflict("Le justificatif n'est disponible qu'une fois l'échange clôturé");
        }
        Payment payment = payments.findFirstByTransactionIdAndStatusOrderByCreatedAtDesc(tx.getId(), PaymentStatus.SUCCESS)
                .orElseThrow(() -> ApiException.conflict("Aucun paiement réussi associé à cette transaction"));
        String number = "REC-%d-%06d".formatted(tx.getCompletedAt().atZone(ZoneOffset.UTC).getYear(), tx.getId());
        return new ReceiptResponse(number, tx.getId(), tx.getReservationId(),
                tx.getBuyerId(), tx.getBuyerHouseholdId(), tx.getSellerId(), tx.getSellerHouseholdId(),
                tx.getEnergyKwh(), tx.getPricePerKwh(), tx.getTotalAmount(),
                payment.getReference(), payment.getMethod(), tx.getPaidAt(), tx.getCompletedAt(), Instant.now());
    }

    // ------------------------------------------------------------------ GET /api/transactions/{id}/history

    @Transactional(readOnly = true)
    public List<TransactionHistory> history(Long id, AuthenticatedUser user) {
        Transaction tx = find(id);
        assertCanView(tx, user);
        return histories.findByTransactionIdOrderByChangedAtAscIdAsc(tx.getId());
    }

    // ------------------------------------------------------------------ helpers

    private Transaction find(Long id) {
        return transactions.findById(id).orElseThrow(() -> ApiException.notFound("Transaction introuvable : " + id));
    }

    private void assertCanView(Transaction tx, AuthenticatedUser user) {
        if (user.admin() || tx.isBuyer(user.userId()) || tx.isSeller(user.userId())) {
            return;
        }
        throw ApiException.forbidden("Accès interdit à cette transaction");
    }

    private void recordHistory(Transaction tx, TransactionStatus from, TransactionStatus to, Long by, String reason) {
        histories.save(TransactionHistory.of(tx.getId(), from, to, by, reason));
    }
}
