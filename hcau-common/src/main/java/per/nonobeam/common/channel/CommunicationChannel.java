package per.nonobeam.common.channel;

public abstract class CommunicationChannel {

  public abstract <ReqT, ResT> ChannelResponse<ResT> push(
      String destination, ChannelRequest<ReqT> request, Class<ResT> responseType);

  public abstract <ReqT, ResT> ChannelResponse<ResT> pull(
      String destination, ChannelRequest<ReqT> request, Class<ResT> responseType);

  protected void onError(String destination, ChannelRequest<?> request, Throwable e) {}

  protected void logRequest(String destination, ChannelRequest<?> request) {}

  protected void logResponse(String destination, ChannelResponse<?> response) {}
}
