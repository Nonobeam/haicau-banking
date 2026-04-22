package per.nonobeam.common.channel;

/**
 * Holds the 3-digit service ID for the running process.
 *
 * <p>Must be initialised once at startup (by the framework bridge in hcau-internal-client) before
 * any {@link ChannelRequest} is created. Any service that imports hcau-common and participates in
 * inter-service communication is required to register its ID.
 */
public final class ServiceContext {

  private static volatile String registeredId;

  private ServiceContext() {}

  /**
   * Register this service's ID. Called once at startup by the framework layer. Validates the value
   * against {@link ServiceId#FORMAT}.
   */
  public static void register(String id) {
    registeredId = ServiceId.validate(id);
  }

  /**
   * Returns the registered service ID.
   *
   * @throws IllegalStateException if {@link #register} was never called — indicates {@code
   *     hcau.service.id} is missing from application.yml
   */
  public static String current() {
    String id = registeredId;
    if (id == null) {
      throw new IllegalStateException(
          "No service ID registered. "
              + "Declare hcau.service.id (exactly 3 digits, e.g. 001) in application.yml");
    }
    return id;
  }
}
