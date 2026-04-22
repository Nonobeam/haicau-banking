package per.nonobeam.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import per.nonobeam.exception.model.res.ErrorCode;

@Getter
@AllArgsConstructor
public enum ApplicationErrorCode implements ErrorCode {
  INVALID_REQUEST_PARAMETER("E_101_400_001", 400),
  INVALID_HTTP_REQUEST_METHOD("E_101_400_002", 400),
  INVALID_HTTP_REQUEST_RESOURCE("E_101_400_003", 400),
  UNSUPPORTED_DOMAIN("E_101_400_004", 400),
  INVALID_REQUEST("E_101_400_005", 400),
  CURRENCY_NOT_REGISTERED("E_101_400_006", 400),
  DUPLICATE_CURRENCY("E_101_400_007", 400),

  UNAUTHORIZED("E_101_401_001", 401),

  FORBIDDEN("E_101_403_001", 403),

  ACCOUNT_NOT_FOUND("E_101_404_001", 404),
  USER_NOT_FOUND("E_101_404_002", 404),
  DOMAIN_NOT_FOUND("E_101_404_003", 404),

  // INTERNAL SERVER ERRORs
  INTERNAL_SERVER_ERROR("E_101_500_001", 500),
  INTERNAL_ERROR_SERVER("E_101_500_002", 500),
  INTERNAL_AUTH_SERVICE_ERROR("E_101_500_003", 500),
  INTERNAL_CALLING_ERROR("E_101_500_004", 500),
  ;

  private final String systemCode;

  private final Integer httpStatusCode;
}
