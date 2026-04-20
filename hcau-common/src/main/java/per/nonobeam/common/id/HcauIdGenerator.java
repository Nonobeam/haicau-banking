package per.nonobeam.common.id;

import com.github.f4b6a3.uuid.UuidCreator;
import java.lang.reflect.Member;
import java.util.EnumSet;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;

public class HcauIdGenerator implements BeforeExecutionGenerator {

  private final String prefix;

  public HcauIdGenerator(HcauId annotation, Member member, GeneratorCreationContext context) {
    this.prefix = annotation.prefix();
  }

  public static String generate(String prefix) {
    String uuidV7 = UuidCreator.getTimeOrderedEpoch().toString().replace("-", "");
    if (prefix == null || prefix.isEmpty()) {
      return uuidV7;
    }
    return prefix + "_" + uuidV7;
  }

  @Override
  public Object generate(
      SharedSessionContractImplementor session,
      Object owner,
      Object currentValue,
      EventType eventType) {
    if (currentValue instanceof String existing && !existing.isEmpty()) {
      return existing;
    }
    return generate(prefix);
  }

  @Override
  public EnumSet<EventType> getEventTypes() {
    return EnumSet.of(EventType.INSERT);
  }
}
