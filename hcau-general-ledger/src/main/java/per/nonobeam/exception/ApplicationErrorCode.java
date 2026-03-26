package per.nonobeam.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import per.nonobeam.exception.model.res.ErrorCode;

@Getter
@AllArgsConstructor
public enum ApplicationErrorCode implements ErrorCode {
  INVALID_REQUEST_PARAMETER("E_100_400_001", 400),
  INVALID_HTTP_REQUEST_METHOD("E_100_400_002", 400),
  INVALID_HTTP_REQUEST_RESOURCE("E_100_400_003", 400),

  UNSUPPORTED_DOMAIN("E_100_400_001", 400),
  INVALID_REQUEST("E_100_400_002", 400),

  UNAUTHORIZED("E_100_401_001", 401),

  FORBIDDEN("E_100_403_001", 403),

  ACCOUNT_NOT_FOUND("E_100_404_001", 404),
  USER_NOT_FOUND("E_100_404_002", 404),
  DOMAIN_NOT_FOUND("E_100_404_003", 404),

  // INTERNAL SERVER ERRORs
  INTERNAL_SERVER_ERROR("E_100_500_001", 500),
  INTERNAL_ERROR_SERVER("E_100_500_001", 500),
  INTERNAL_AUTH_SERVICE_ERROR("E_101_500_001", 500),
  INTERNAL_CALLING_ERROR("E_100_500_002", 500),
  ;

  private final String systemCode;

  private final Integer httpStatusCode;
}
