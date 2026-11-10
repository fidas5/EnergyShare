package com.energyshare.transaction_service.messaging;

import com.energyshare.transaction_service.config.EventsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publie l'evenement APRES le commit de la transaction SQL, pour ne jamais annoncer
 * un changement qui aurait ete annule en base.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final EventsProperties props;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TransactionEvent event) {
        String routingKey = TransactionEvent.CONFIRMED.equals(event.eventType())
                ? props.confirmedKey() : props.cancelledKey();
        try {
            rabbitTemplate.convertAndSend(props.exchange(), routingKey, event);
            log.info("Evenement {} publie pour la transaction {}", event.eventType(), event.transactionId());
        } catch (AmqpException e) {
            // Best effort : la transaction SQL est deja validee. Un pattern "outbox" rendrait cela fiable.
            log.error("Echec de publication de {} (transaction {}) : {}", event.eventType(), event.transactionId(), e.getMessage());
        }
    }
}
