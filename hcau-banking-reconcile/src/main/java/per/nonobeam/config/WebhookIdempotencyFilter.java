package per.nonobeam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class WebhookIdempotencyFilter extends OncePerRequestFilter {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/webhooks/deposit");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
    String body = wrappedRequest.getCachedBodyAsString();
    String eventId = extractEventId(body);

    if (eventId != null && !eventId.isBlank()) {
      HttpServletRequest withKey =
          new HttpServletRequestWrapperWithIdempotencyHeader(wrappedRequest, eventId);
      filterChain.doFilter(withKey, response);
      return;
    }

    filterChain.doFilter(wrappedRequest, response);
  }

  private String extractEventId(String body) {
    try {
      JsonNode root = objectMapper.readTree(body);
      JsonNode eventId = root.get("eventId");
      if (eventId != null && !eventId.isNull()) {
        return eventId.asText();
      }
      return null;
    } catch (Exception ex) {
      return null;
    }
  }
}
