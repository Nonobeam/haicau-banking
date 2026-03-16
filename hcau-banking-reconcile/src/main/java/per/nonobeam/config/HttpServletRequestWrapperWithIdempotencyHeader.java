package per.nonobeam.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class HttpServletRequestWrapperWithIdempotencyHeader extends HttpServletRequestWrapper {

  private final String idempotencyKey;

  public HttpServletRequestWrapperWithIdempotencyHeader(
      HttpServletRequest request, String idempotencyKey) {
    super(request);
    this.idempotencyKey = idempotencyKey;
  }

  @Override
  public String getHeader(String name) {
    if ("Idempotency-Key".equalsIgnoreCase(name)) {
      return idempotencyKey;
    }
    return super.getHeader(name);
  }

  @Override
  public Enumeration<String> getHeaders(String name) {
    if ("Idempotency-Key".equalsIgnoreCase(name)) {
      return Collections.enumeration(List.of(idempotencyKey));
    }
    return super.getHeaders(name);
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    List<String> names = new ArrayList<>();
    Enumeration<String> headerNames = super.getHeaderNames();
    while (headerNames.hasMoreElements()) {
      names.add(headerNames.nextElement());
    }
    if (names.stream().noneMatch(n -> "Idempotency-Key".equalsIgnoreCase(n))) {
      names.add("Idempotency-Key");
    }
    return Collections.enumeration(names);
  }
}
