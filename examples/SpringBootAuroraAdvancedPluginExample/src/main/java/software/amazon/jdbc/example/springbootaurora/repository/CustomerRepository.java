package software.amazon.jdbc.example.springbootaurora.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import software.amazon.jdbc.example.springbootaurora.model.Customer;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    // JpaRepository provides CRUD methods like save(), findById(), findAll(), etc.
    // Custom query methods can be added here if needed.
}
