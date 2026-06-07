package per.nonobeam.web.advice;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.AccessDeniedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.validation.DuplicateRequestException;
import per.nonobeam.validation.InvalidCoaPathException;
import per.nonobeam.validation.SameAccountException;
import per.nonobeam.validation.UserLimitExceededException;
import per.nonobeam.validation.WalletInactiveException;
import tools.jackson.databind.exc.InvalidFormatException;

@Slf4j
@ControllerAdvice
public class ControllerAdviceHandler extends ExceptionHandlerAdvice {

  public ControllerAdviceHandler(MessageSource messageSource) {
    super(messageSource);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<?> handle(HttpServletRequest request, MethodArgumentNotValidException e) {
    return error(
        ApplicationErrorCode.INVALID_REQUEST_PARAMETER, extractFieldErrors(e.getBindingResult()));
  }

  @ExceptionHandler(InvalidFormatException.class)
  public ResponseEntity<?> handle(final HttpServletRequest request, InvalidFormatException e) {
    log.error("Error during parsing request: {}", e.getMessage());
    return error(ApplicationErrorCode.INVALID_REQUEST_PARAMETER);
  }

  @ExceptionHandler(ApplicationException.class)
  public ResponseEntity<?> handle(final HttpServletRequest request, ApplicationException e) {
    log.warn(
        "ApplicationException at [{} {}] - Code: {}, Message: {}",
        request.getMethod(),
        request.getRequestURI(),
        e.getErrorCode().getSystemCode(),
        e.getMessage());
    return error(e.getErrorCode(), e.getArgs());
  }

  @ExceptionHandler(UserLimitExceededException.class)
  public ResponseEntity<?> handle(HttpServletRequest request, UserLimitExceededException e) {
    log.warn(
        "Rate limit at [{} {}]: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
    return error(ApplicationErrorCode.SAGA_RATE_LIMIT_EXCEEDED);
  }

  @ExceptionHandler(DuplicateRequestException.class)
  public ResponseEntity<?> handle(HttpServletRequest request, DuplicateRequestException e) {
    log.warn(
        "Duplicate request at [{} {}]: {}",
        request.getMethod(),
        request.getRequestURI(),
        e.getMessage());
    return error(ApplicationErrorCode.DUPLICATE_REQUEST);
  }

  @ExceptionHandler(InvalidCoaPathException.class)
  public ResponseEntity<?> handle(HttpServletRequest request, InvalidCoaPathException e) {
    log.warn(
        "Invalid CoA path at [{} {}]: {}",
        request.getMethod(),
        request.getRequestURI(),
        e.getMessage());
    return error(ApplicationErrorCode.INVALID_COA_PATH, e.getPath());
  }

  @ExceptionHandler(WalletInactiveException.class)
  public ResponseEntity<?> handle(HttpServletRequest request, WalletInactiveException e) {
    log.warn(
        "Wallet inactive at [{} {}]: {}",
        request.getMethod(),
        request.getRequestURI(),
        e.getMessage());
    return error(ApplicationErrorCode.WALLET_INACTIVE, e.getWalletId());
  }

  @ExceptionHandler(SameAccountException.class)
  public ResponseEntity<?> handle(HttpServletRequest request, SameAccountException e) {
    log.warn(
        "Same account at [{} {}]: {}",
        request.getMethod(),
        request.getRequestURI(),
        e.getMessage());
    return error(ApplicationErrorCode.SAME_ACCOUNT);
  }

  @ExceptionHandler(Throwable.class)
  public ResponseEntity<?> handle(HttpServletRequest request, Throwable e) {
    log.error("Unhandled exception at [{} {}]", request.getMethod(), request.getRequestURI(), e);
    return error(ApplicationErrorCode.INTERNAL_SERVER_ERROR, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<?> handle(
      HttpServletRequest request, MethodArgumentTypeMismatchException e) {
    return error(ApplicationErrorCode.INVALID_REQUEST_PARAMETER, e.getName());
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<?> handle(final HttpServletRequest request, AccessDeniedException e) {
    log.warn(
        "AccessDeniedException at [{} {}] - Message: {}",
        request.getMethod(),
        request.getRequestURI(),
        e.getMessage(),
        e);
    return error(ApplicationErrorCode.FORBIDDEN);
  }

  @ExceptionHandler(HttpClientErrorException.class)
  public ResponseEntity<?> handle(final HttpServletRequest request, HttpClientErrorException e) {
    log.warn(
        "HttpClientErrorException at [{} {}] - Message: {}",
        request.getMethod(),
        request.getRequestURI(),
        e.getMessage());
    return error(ApplicationErrorCode.INTERNAL_ERROR_SERVER);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<?> handle(
      final HttpServletRequest request, HttpMessageNotReadableException e) {
    log.warn(
        "HttpMessageNotReadableException at [{} {}] - Message: {}",
        request.getMethod(),
        request.getRequestURI(),
        e.getMessage());
    return error(ApplicationErrorCode.INVALID_REQUEST_PARAMETER);
  }
}
