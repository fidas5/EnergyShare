package com.energyshare.transaction_service.security;

import com.energyshare.transaction_service.exception.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Construit l'utilisateur courant a partir des en-tetes ajoutes par l'API Gateway apres validation du jeton :
 * X-User-Id (obligatoire), X-User-Role (ADMIN ou USER), X-Household-Id.
 * Absence de X-User-Id => 401.
 */
@Component
public class AuthenticatedUserResolver implements HandlerMethodArgumentResolver {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLE = "X-User-Role";
    public static final String HOUSEHOLD_ID = "X-Household-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AuthenticatedUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String rawUserId = webRequest.getHeader(USER_ID);
        if (rawUserId == null || rawUserId.isBlank()) {
            throw ApiException.unauthorized("Authentification requise");
        }
        Long userId = parse(rawUserId, USER_ID);
        String rawHousehold = webRequest.getHeader(HOUSEHOLD_ID);
        Long householdId = (rawHousehold == null || rawHousehold.isBlank()) ? null : parse(rawHousehold, HOUSEHOLD_ID);
        boolean admin = "ADMIN".equalsIgnoreCase(webRequest.getHeader(USER_ROLE));
        return new AuthenticatedUser(userId, householdId, admin);
    }

    private Long parse(String value, String header) {
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw ApiException.unauthorized("En-tête " + header + " invalide");
        }
    }
}
