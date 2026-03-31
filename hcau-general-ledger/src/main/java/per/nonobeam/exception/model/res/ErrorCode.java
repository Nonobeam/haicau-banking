package per.nonobeam.exception.model.res;

import com.fasterxml.jackson.annotation.JsonValue;

public interface ErrorCode {
  Integer getHttpStatusCode();

  @JsonValue
  String getSystemCode();
}
