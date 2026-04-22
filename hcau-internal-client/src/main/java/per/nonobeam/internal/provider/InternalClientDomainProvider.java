package per.nonobeam.internal.provider;

public class InternalClientDomainProvider {

  private final InternalClientDomainSpecification spec;

  public InternalClientDomainProvider(InternalClientDomainConfigurer configurer) {
    this.spec = new InternalClientDomainSpecification();
    configurer.configure(this.spec);
  }

  public ServiceConfig getService(Class<?> serviceClass) {
    return spec.getService(serviceClass);
  }
}
