package per.nonobeam.common.id;

import com.github.f4b6a3.ulid.UlidCreator;
import java.lang.reflect.Member;
import java.util.EnumSet;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;

public class UlidIdentifierGenerator implements BeforeExecutionGenerator {

  private final String prefix;

  public UlidIdentifierGenerator(
      UlidGeneratedId annotation, Member member, GeneratorCreationContext context) {
    this.prefix = annotation.prefix();
  }

  @Override
  public Object generate(
      SharedSessionContractImplementor session,
      Object owner,
      Object currentValue,
      EventType eventType) {
    return prefix + "_" + UlidCreator.getUlid().toLowerCase();
  }

  @Override
  public EnumSet<EventType> getEventTypes() {
    return EnumSet.of(EventType.INSERT);
  }
}
