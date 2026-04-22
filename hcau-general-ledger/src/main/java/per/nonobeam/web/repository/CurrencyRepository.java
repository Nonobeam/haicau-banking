package per.nonobeam.web.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import per.nonobeam.web.common.currency.Currency;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, String> {

  List<Currency> findByCodeInAndIsActiveTrue(Collection<String> codes);

  List<Currency> findByIsActiveTrueOrderByCodeAsc();
}
