package per.nonobeam.web.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import per.nonobeam.web.BaseIntegrationTest;
import per.nonobeam.web.common.currency.Currency;
import per.nonobeam.web.testutil.CurrencyTestFixtures;

class CurrencyRepositoryIntegrationTest extends BaseIntegrationTest {

  @Autowired private CurrencyRepository currencyRepository;

  @BeforeEach
  void seedUsd() {
    CurrencyTestFixtures.ensureUsd(currencyRepository);
  }

  @Test
  @Transactional
  void findByCodeInAndIsActiveTrue_returnsOnlyActive() {
    currencyRepository.save(
        Currency.builder()
            .code("EUR")
            .name("Euro")
            .decimalPlaces((short) 2)
            .displayDecimals((short) 2)
            .isActive(true)
            .build());

    List<Currency> result = currencyRepository.findByCodeInAndIsActiveTrue(List.of("USD", "EUR"));

    assertThat(result).extracting(Currency::getCode).containsExactlyInAnyOrder("USD", "EUR");
  }

  @Test
  @Transactional
  void findByCodeInAndIsActiveTrue_filtersInactive() {
    currencyRepository.save(
        Currency.builder()
            .code("JPY")
            .name("Japanese Yen")
            .decimalPlaces((short) 0)
            .displayDecimals((short) 0)
            .isActive(false)
            .build());

    List<Currency> result = currencyRepository.findByCodeInAndIsActiveTrue(List.of("JPY"));

    assertThat(result).isEmpty();
  }

  @Test
  @Transactional
  void findByCodeInAndIsActiveTrue_emptyInput_returnsEmpty() {
    List<Currency> result = currencyRepository.findByCodeInAndIsActiveTrue(List.of());

    assertThat(result).isEmpty();
  }
}
