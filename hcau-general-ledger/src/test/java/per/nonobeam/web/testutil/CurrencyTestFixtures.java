package per.nonobeam.web.testutil;

import per.nonobeam.web.common.currency.Currency;
import per.nonobeam.web.repository.CurrencyRepository;

public final class CurrencyTestFixtures {

  private CurrencyTestFixtures() {}

  public static Currency ensureUsd(CurrencyRepository repo) {
    return repo.findById("USD")
        .orElseGet(
            () ->
                repo.save(
                    Currency.builder()
                        .code("USD")
                        .name("US Dollar")
                        .decimalPlaces((short) 2)
                        .displayDecimals((short) 2)
                        .isActive(true)
                        .build()));
  }

  public static Currency ensureEur(CurrencyRepository repo) {
    return repo.findById("EUR")
        .orElseGet(
            () ->
                repo.save(
                    Currency.builder()
                        .code("EUR")
                        .name("Euro")
                        .decimalPlaces((short) 2)
                        .displayDecimals((short) 2)
                        .isActive(true)
                        .build()));
  }
}
