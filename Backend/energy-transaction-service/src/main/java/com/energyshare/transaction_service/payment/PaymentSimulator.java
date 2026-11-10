package com.energyshare.transaction_service.payment;

import com.energyshare.transaction_service.domain.PaymentMethod;
import com.energyshare.transaction_service.exception.ApiException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Paiement simule : succes par defaut, jetons speciaux pour tester les cas d'erreur. */
@Component
public class PaymentSimulator {

    public static final String TOKEN_DECLINED = "tok_declined";
    public static final String TOKEN_UNAVAILABLE = "tok_unavailable";

    public PaymentResult processPayment(BigDecimal amount, PaymentMethod method, String token) {
        if (TOKEN_UNAVAILABLE.equals(token)) {
            throw ApiException.unavailable("Service de paiement indisponible");
        }
        if (TOKEN_DECLINED.equals(token)) {
            return PaymentResult.declined("Paiement refusé (simulation)");
        }
        return PaymentResult.success("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}
