package per.nonobeam.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import per.nonobeam.common.config.IdempotencyKey;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.repository.IdempotencyConfigRepository;
import per.nonobeam.repository.IdempotencyKeyRepository;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class IdempotencyFilter extends OncePerRequestFilter {

  private final IdempotencyKeyRepository idempotencyKeyRepository;
  private final IdempotencyConfigRepository idempotencyConfigRepository;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !"POST".equalsIgnoreCase(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String idempotencyKey = request.getHeader("Idempotency-Key");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response
          .getWriter()
          .write(
              "{\"error\":{\"code\":\""
                  + ApplicationErrorCode.IDEMPOTENCY_KEY_REQUIRED.getSystemCode()
                  + "\",\"message\":\"Idempotency key required\"}}");
      return;
    }

    OffsetDateTime now = OffsetDateTime.now();
    var existing =
        idempotencyKeyRepository.findByIdempotencyKeyAndExpiresAtAfter(idempotencyKey, now);
    if (existing.isPresent()) {
      IdempotencyKey cached = existing.get();
      response.setStatus(cached.getResponseStatus());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write(cached.getResponseBody());
      return;
    }

    CachedBodyHttpServletResponse cachedResponse = new CachedBodyHttpServletResponse(response);
    filterChain.doFilter(request, cachedResponse);

    int keyExpiryHours =
        idempotencyConfigRepository
            .findById("key_expiry_hours")
            .map(config -> Integer.parseInt(config.getValue()))
            .orElse(24);

    if (cachedResponse.getStatus() >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
      cachedResponse.copyBodyToResponse();
      return;
    }

    IdempotencyKey record =
        IdempotencyKey.builder()
            .idempotencyKey(idempotencyKey)
            .responseStatus(cachedResponse.getStatus())
            .responseBody(cachedResponse.getCachedBodyAsString())
            .expiresAt(now.plusHours(keyExpiryHours))
            .build();
    idempotencyKeyRepository.save(record);

    cachedResponse.copyBodyToResponse();
  }
}
