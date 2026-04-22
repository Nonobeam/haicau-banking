package per.nonobeam.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import per.nonobeam.exception.ApplicationErrorCode;
import per.nonobeam.exception.ApplicationException;
import per.nonobeam.web.common.currency.Currency;
import per.nonobeam.web.repository.CurrencyRepository;

@ExtendWith(MockitoExtension.class)
class CurrencyValidatorTest {

  @Mock private CurrencyRepository currencyRepository;

  @InjectMocks private CurrencyValidator validator;

  private Currency currency(String code) {
    return Currency.builder()
        .code(code)
        .name(code)
        .decimalPlaces((short) 2)
        .displayDecimals((short) 2)
        .isActive(true)
        .build();
  }

  @Test
  void requireAllActive_allActiveCodes_returnsRows() {
    // given
    when(currencyRepository.findByCodeInAndIsActiveTrue(anyCollection()))
        .thenReturn(List.of(currency("USD"), currency("EUR")));

    // when
    List<Currency> result = validator.requireAllActive(List.of("USD", "EUR"));

    // then
    assertThat(result).extracting(Currency::getCode).containsExactlyInAnyOrder("USD", "EUR");
  }

  @Test
  void requireAllActive_missingCode_throwsCurrencyNotRegistered() {
    // given
    when(currencyRepository.findByCodeInAndIsActiveTrue(anyCollection()))
        .thenReturn(List.of(currency("USD")));

    // when / then
    assertThatThrownBy(() -> validator.requireAllActive(List.of("USD", "XYZ")))
        .isInstanceOf(ApplicationException.class)
        .extracting(e -> ((ApplicationException) e).getErrorCode())
        .isEqualTo(ApplicationErrorCode.CURRENCY_NOT_REGISTERED);
  }

  @Test
  void requireAllActive_inactiveCode_throwsCurrencyNotRegistered() {
    // given — repository only returns active; inactive code shows up as missing
    when(currencyRepository.findByCodeInAndIsActiveTrue(anyCollection())).thenReturn(List.of());

    // when / then
    assertThatThrownBy(() -> validator.requireAllActive(List.of("JPY")))
        .isInstanceOf(ApplicationException.class)
        .extracting(e -> ((ApplicationException) e).getErrorCode())
        .isEqualTo(ApplicationErrorCode.CURRENCY_NOT_REGISTERED);
  }

  @Test
  void requireAllActive_emptyInput_returnsEmptyList() {
    // when
    List<Currency> result = validator.requireAllActive(List.of());

    // then
    assertThat(result).isEmpty();
  }
}
