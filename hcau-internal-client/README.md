# hcau-internal-client

Internal HTTP client module for type-safe, declarative inter-service communication within the HCAU banking platform. Built on Spring Framework 7's `RestClient` and `HttpServiceProxyFactory` with Apache HttpClient 5 connection pooling.

---

## How it works

1. **Define an exchange interface** — annotate methods with `@HttpExchange`, `@GetExchange`, `@PostExchange`, etc.
2. **Register the service URL** — via `InternalClientDomainConfigurer` bean.
3. **Create a service manager** — extend `BaseServiceManager` and delegate to the exchange proxy.
4. **Enable the module** — annotate your `@SpringBootApplication` (or any `@Configuration`) with `@InternalHttpClient`.

---

## Step-by-step usage

### 1. Add the dependency

```xml
<dependency>
  <groupId>per.nonobeam</groupId>
  <artifactId>hcau-internal-client</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

### 2. Define an exchange interface

Extends `BaseExchange` and use Spring's declarative HTTP annotations.

```java
package per.nonobeam.web.exchange;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import per.nonobeam.internal.exchange.BaseExchange;

@HttpExchange(url = "/internal")
public interface HttpPlatformExchange extends BaseExchange {

    @GetExchange("/users/{id}")
    UserResponse getUser(@PathVariable("id") String id);

    @PostExchange("/users")
    UserResponse createUser(@RequestBody CreateUserRequest request);
}
```

### 3. Define a service interface and implementation

```java
// Service interface
public interface PlatformService extends BaseService {
    UserResponse getUser(String id);
    UserResponse createUser(CreateUserRequest request);
}

// Service implementation
@Service
@ConditionalOnProperty(name = "micro.services.platform-service.enabled", havingValue = "true")
public class PlatformServiceManager extends BaseServiceManager implements PlatformService {

    private final HttpPlatformExchange exchange;

    public PlatformServiceManager(
            @Qualifier("internalHttpClientRequestFactory") HttpComponentsClientHttpRequestFactory requestFactory,
            @Qualifier("internalResponseErrorHandler") ResponseErrorHandler errorHandler,
            InternalClientDomainProvider domainProvider) {
        super(requestFactory, errorHandler);
        this.exchange = new HttpServiceFactory<HttpPlatformExchange>(requestFactory, errorHandler)
                .create(HttpPlatformExchange.class, domainProvider);
    }

    @Override
    public UserResponse getUser(String id) {
        return exchange.getUser(id);
    }

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        return exchange.createUser(request);
    }
}
```

### 4. Register the service URL and enable the module

```java
@InternalHttpClient
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    InternalClientDomainConfigurer internalClientDomainConfigurer() {
        return spec -> spec
                .service(HttpPlatformExchange.class, "http://hcau-platform-service");
    }
}
```

Or in a dedicated `@Configuration` class:

```java
@Configuration
public class InternalClientConfig {

    @Value("${micro.services.platform-service.url}")
    private String platformServiceUrl;

    @Bean
    InternalClientDomainConfigurer internalClientDomainConfigurer() {
        return spec -> spec
                .service(HttpPlatformExchange.class, platformServiceUrl);
    }
}
```

### 5. Configure connection pool settings (optional)

All settings have sensible defaults. Override in `application.yml` as needed:

```yaml
internal:
  http-client:
    connect-timeout: 30      # seconds, default 30
    socket-timeout: 30       # seconds, default 30
    keep-alive-timeout: 60   # seconds, default 60
    max-total: 100           # max total connections, default 100
    max-per-route: 20        # max connections per route, default 20

micro:
  services:
    platform-service:
      enabled: true
      url: http://hcau-platform-service
```

---

## Adding per-service interceptors

Use `.withInterceptor(…)` on the service builder to attach request interceptors — useful for adding auth headers or request tracing:

```java
@Bean
InternalClientDomainConfigurer internalClientDomainConfigurer(MyAuthInterceptor authInterceptor) {
    return spec -> spec
            .service(HttpPlatformExchange.class, platformServiceUrl)
            .withInterceptor(authInterceptor)
            .withInterceptor(new RequestLoggingInterceptor());
}
```

Interceptors are chained in registration order. Implement `ClientHttpRequestInterceptor`:

```java
@Component
public class MyAuthInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set("X-Internal-Token", "secret");
        return execution.execute(request, body);
    }
}
```

---

## Complete example

Scenario: `hcau-general-ledger` needs to call `hcau-platform-service` to look up a user by ID.

**`HttpPlatformExchange.java`** — declare the remote API:
```java
package per.nonobeam.web.exchange;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import per.nonobeam.internal.exchange.BaseExchange;
import per.nonobeam.web.model.UserResponse;

@HttpExchange(url = "/internal")
public interface HttpPlatformExchange extends BaseExchange {

    @GetExchange("/users/{id}")
    UserResponse getUser(@PathVariable("id") String id);
}
```

**`PlatformService.java`** — public-facing interface:
```java
package per.nonobeam.web.service;

import per.nonobeam.internal.factory.BaseService;
import per.nonobeam.web.model.UserResponse;

public interface PlatformService extends BaseService {
    UserResponse getUser(String id);
}
```

**`PlatformServiceManager.java`** — implementation that wires the exchange proxy:
```java
package per.nonobeam.web.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResponseErrorHandler;
import per.nonobeam.internal.factory.BaseServiceManager;
import per.nonobeam.internal.factory.HttpServiceFactory;
import per.nonobeam.internal.provider.InternalClientDomainProvider;
import per.nonobeam.web.exchange.HttpPlatformExchange;
import per.nonobeam.web.model.UserResponse;
import per.nonobeam.web.service.PlatformService;

@Slf4j
@Service
@ConditionalOnProperty(name = "micro.services.platform-service.enabled", havingValue = "true")
public class PlatformServiceManager extends BaseServiceManager implements PlatformService {

    private final HttpPlatformExchange exchange;

    public PlatformServiceManager(
            @Qualifier("internalHttpClientRequestFactory") HttpComponentsClientHttpRequestFactory requestFactory,
            @Qualifier("internalResponseErrorHandler") ResponseErrorHandler errorHandler,
            InternalClientDomainProvider domainProvider) {
        super(requestFactory, errorHandler);
        this.exchange = new HttpServiceFactory<HttpPlatformExchange>(requestFactory, errorHandler)
                .create(HttpPlatformExchange.class, domainProvider);
    }

    @Override
    public UserResponse getUser(String id) {
        log.debug("Fetching user {} from platform service", id);
        return exchange.getUser(id);
    }
}
```

**`Application.java`** — enable the module and register the URL:
```java
package per.nonobeam.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import per.nonobeam.internal.InternalHttpClient;
import per.nonobeam.internal.provider.InternalClientDomainConfigurer;
import per.nonobeam.web.exchange.HttpPlatformExchange;

@InternalHttpClient
@SpringBootApplication
public class Application {

    @Value("${micro.services.platform-service.url}")
    private String platformServiceUrl;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    InternalClientDomainConfigurer internalClientDomainConfigurer() {
        return spec -> spec.service(HttpPlatformExchange.class, platformServiceUrl);
    }
}
```

**`application.yml`**:
```yaml
micro:
  services:
    platform-service:
      enabled: true
      url: http://hcau-platform-service

internal:
  http-client:
    connect-timeout: 30
    socket-timeout: 30
    keep-alive-timeout: 60
    max-total: 100
    max-per-route: 20
```

**Consuming the service** in any other bean:
```java
@Service
@RequiredArgsConstructor
public class AccountService {

    private final PlatformService platformService;

    public void doSomething(String userId) {
        UserResponse user = platformService.getUser(userId);
        // ...
    }
}
```

---

## Module structure

```
per.nonobeam.internal/
├── InternalHttpClient.java                          ← enable annotation (@Import config)
├── InternalRestClientConfig.java                    ← all infrastructure beans
├── PackageMarker.java
├── exchange/
│   └── BaseExchange.java                            ← marker for exchange interfaces
├── factory/
│   ├── BaseService.java                             ← marker for service interfaces
│   ├── BaseServiceManager.java                      ← abstract base for service impls
│   └── HttpServiceFactory.java                      ← creates declarative HTTP proxies
├── interceptor/
│   └── CompositeClientHttpRequestInterceptor.java   ← chains multiple interceptors
├── provider/
│   ├── InternalClientDomainConfigurer.java          ← functional config DSL
│   ├── InternalClientDomainProvider.java
│   ├── InternalClientDomainSpecification.java       ← registers service URLs
│   └── ServiceConfig.java
└── setting/
    └── InternalHttpClientSetting.java               ← @ConfigurationProperties
```
