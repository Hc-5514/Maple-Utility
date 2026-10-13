package com.maple.utility.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.maple.utility.exception.ApiException;
import com.maple.utility.exception.ErrorResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtTokenProvider jwtTokenProvider;
	private final ObjectMapper objectMapper;
    private final com.maple.utility.service.DataContextService contexts;
    private final org.springframework.transaction.support.TransactionTemplate transaction;

	public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper,
            com.maple.utility.service.DataContextService contexts,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.objectMapper = objectMapper;
        this.contexts = contexts;
        this.transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String token = resolveToken(request);
		if (token == null) {
			filterChain.doFilter(request, response);
			return;
		}

        try {
            JwtAuthentication parsed = jwtTokenProvider.parseAccessToken(token);
            transaction.executeWithoutResult(status -> {
                boolean switching = request.getRequestURI().equals("/api/v1/auth/data-context")
                        || (request.getRequestURI().startsWith("/api/v1/api-key") && !"GET".equals(request.getMethod()));
                if (switching) contexts.lockConnections(); else contexts.lockBusinessRequest();
                JwtAuthentication principal = contexts.resolve(parsed);
                var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                try (var scope = CredentialScope.use(principal.credentialId() == null ? -1L : principal.credentialId())) {
                    filterChain.doFilter(request, response);
                } catch (ServletException | IOException exception) {
                    throw new FilterFailure(exception);
                }
            });
        } catch (ApiException exception) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response, exception);
        } catch (FilterFailure failure) {
            if (failure.getCause() instanceof IOException exception) throw exception;
            throw (ServletException) failure.getCause();
        }

	}

    private static class FilterFailure extends RuntimeException {
        FilterFailure(Exception cause) { super(cause); }
    }

    private String resolveToken(HttpServletRequest request) {
		String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
			return null;
		}
		return authorization.substring(BEARER_PREFIX.length());
	}

	private void writeErrorResponse(HttpServletResponse response, ApiException exception) throws IOException {
		response.setStatus(exception.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(), ErrorResponse.of(exception.getCode(), exception.getMessage()));
	}
}
