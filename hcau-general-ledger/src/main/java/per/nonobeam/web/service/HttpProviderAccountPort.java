package per.nonobeam.web.service;

import org.springframework.stereotype.Component;
import per.nonobeam.common.channel.ChannelRequest;
import per.nonobeam.common.provider.AbstractProviderAccountPort;
import per.nonobeam.common.provider.CreateProviderAccountRequest;
import per.nonobeam.common.provider.ProvisionedAccount;
import per.nonobeam.internal.channel.HttpCommunicationChannel;

@Component
public class HttpProviderAccountPort extends AbstractProviderAccountPort {

  private static final String DESTINATION = "platform-service/internal/v1/provider-accounts";

  private final HttpCommunicationChannel channel;

  public HttpProviderAccountPort(HttpCommunicationChannel channel) {
    this.channel = channel;
  }

  @Override
  public ProvisionedAccount createProviderAccount(String userId) {
    return channel
        .pull(
            DESTINATION,
            ChannelRequest.of(new CreateProviderAccountRequest(userId)),
            ProvisionedAccount.class)
        .body();
  }
}
