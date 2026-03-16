package per.nonobeam.config;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class CachedBodyHttpServletResponse extends HttpServletResponseWrapper {

  private final ByteArrayOutputStream cachedBody = new ByteArrayOutputStream();
  private ServletOutputStream outputStream;
  private PrintWriter writer;

  public CachedBodyHttpServletResponse(HttpServletResponse response) {
    super(response);
  }

  @Override
  public ServletOutputStream getOutputStream() {
    if (writer != null) {
      throw new IllegalStateException("getWriter() already called");
    }
    if (outputStream == null) {
      outputStream =
          new ServletOutputStream() {
            @Override
            public void write(int b) {
              cachedBody.write(b);
            }

            @Override
            public boolean isReady() {
              return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
              // no-op for cached response wrapper
            }
          };
    }
    return outputStream;
  }

  @Override
  public PrintWriter getWriter() throws IOException {
    if (outputStream != null) {
      throw new IllegalStateException("getOutputStream() already called");
    }
    if (writer == null) {
      writer = new PrintWriter(new OutputStreamWriter(cachedBody, StandardCharsets.UTF_8), true);
    }
    return writer;
  }

  public String getCachedBodyAsString() {
    return cachedBody.toString(StandardCharsets.UTF_8);
  }

  public void copyBodyToResponse() throws IOException {
    if (writer != null) {
      writer.flush();
    }
    HttpServletResponse response = (HttpServletResponse) getResponse();
    byte[] body = cachedBody.toByteArray();
    if (body.length > 0) {
      response.getOutputStream().write(body);
      response.getOutputStream().flush();
    }
  }
}
