package rs.ac.soas.wallet;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingRepository extends JpaRepository<Holding, Long> {
  List<Holding> findByEmailOrderByCurrency(String email);

  List<Holding> findAllByOrderByEmailAscCurrencyAsc();

  Optional<Holding> findByEmailAndCurrency(String email, String currency);

  void deleteByEmail(String email);
}
