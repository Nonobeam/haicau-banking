package per.nonobeam.exception.model.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.ResponseEntity;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResp<T> {
  private boolean success;
  private T data;
  private ErrorResp error;

  @JsonUnwrapped private PagingData<?> pagingData;

  public static <T> ResponseEntity<ApiResp<T>> success(T data) {
    return ResponseEntity.ok(ApiResp.<T>builder().success(true).data(data).build());
  }

  public static <T> ResponseEntity<ApiResp<T>> successPaging(PagingResponseData<T> pagingResponse) {
    return ResponseEntity.ok(
        ApiResp.<T>builder()
            .success(true)
            .pagingData(new PagingData<>(pagingResponse.getData(), pagingResponse.getPagination()))
            .build());
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ErrorResp {
    private ErrorCode code;
    private String message;
    private Object details;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PagingData<T> {
    private java.util.List<T> data;
    private Object pagination;
  }

  public interface PagingResponseData<T> {
    java.util.List<T> getData();

    Object getPagination();
  }
}
