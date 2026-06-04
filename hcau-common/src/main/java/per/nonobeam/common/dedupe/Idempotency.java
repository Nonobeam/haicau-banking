package per.nonobeam.common.dedupe;

public interface Idempotency {
  void excute(String key, String value, Runnable method);
}
