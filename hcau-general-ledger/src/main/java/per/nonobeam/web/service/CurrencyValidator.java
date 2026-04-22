package per.nonobeam.web.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.common.currency.Currency;
import per.nonobeam.web.repository.CurrencyRepository;

@Service
@RequiredArgsConstructor
public class CurrencyValidator {

  private final CurrencyRepository currencyRepository;

  public List<Currency> requireAllActive(Collection<String> codes) {
    if (codes == null || codes.isEmpty()) {
      return List.of();
    }
    List<Currency> found = currencyRepository.findByCodeInAndIsActiveTrue(codes);
    Set<String> foundCodes = found.stream().map(Currency::getCode).collect(Collectors.toSet());
    for (String code : new HashSet<>(codes)) {
      if (!foundCodes.contains(code)) {
        throw new ApplicationException(ApplicationErrorCode.CURRENCY_NOT_REGISTERED, code);
      }
    }
    return found;
  }
}
