package rs.ac.soas.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import rs.ac.soas.contract.Role;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
  Optional<UserEntity> findByEmail(String email);

  boolean existsByRole(Role role);
}
