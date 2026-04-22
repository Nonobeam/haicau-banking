package per.nonobeam.web.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import per.nonobeam.web.common.currency.Currency;
import per.nonobeam.web.repository.CurrencyRepository;

@RestController
@RequiredArgsConstructor
public class CurrencyController {

  private final CurrencyRepository currencyRepository;

  @GetMapping("/internal/v1/currencies")
  public List<Currency> listActiveCurrencies() {
    return currencyRepository.findByIsActiveTrueOrderByCodeAsc();
  }
}
