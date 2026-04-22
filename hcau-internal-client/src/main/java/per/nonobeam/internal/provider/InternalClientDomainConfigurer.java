package per.nonobeam.internal.provider;

@FunctionalInterface
public interface InternalClientDomainConfigurer {
  void configure(InternalClientDomainSpecification spec);
}
