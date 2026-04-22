package per.nonobeam.exception;

import lombok.Getter;
import per.nonobeam.exception.model.res.ErrorCode;

@Getter
public class ApplicationException extends RuntimeException {

  private static final long serialVersionUID = -6793147258106734653L;

  private final ErrorCode errorCode;
  private final Object[] args;

  public ApplicationException(ErrorCode errorCode, Object... args) {
    this.errorCode = errorCode;
    this.args = args;
  }
}
