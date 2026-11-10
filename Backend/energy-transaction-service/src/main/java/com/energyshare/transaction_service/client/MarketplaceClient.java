package com.energyshare.transaction_service.client;

import com.energyshare.transaction_service.config.MarketplaceProperties;
import com.energyshare.transaction_service.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Appel synchrone au Marketplace Service : GET /reservations/{id}. */
@Slf4j
@Component
public class MarketplaceClient {

    private final RestClient restClient;

    public MarketplaceClient(MarketplaceProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build();
    }

    public ReservationDto getReservation(Long reservationId) {
        try {
            ReservationDto reservation = restClient.get()
                    .uri("/reservations/{id}", reservationId)
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw ApiException.notFound("Réservation introuvable : " + reservationId);
                    })
                    .body(ReservationDto.class);
            if (reservation == null) {
                throw ApiException.unavailable("Réponse vide du Marketplace Service");
            }
            return reservation;
        } catch (RestClientException e) {
            log.error("Marketplace Service injoignable ou en erreur : {}", e.getMessage());
            throw ApiException.unavailable("Marketplace Service indisponible");
        }
    }
}
