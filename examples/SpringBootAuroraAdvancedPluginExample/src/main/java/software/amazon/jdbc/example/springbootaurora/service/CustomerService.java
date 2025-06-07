package software.amazon.jdbc.example.springbootaurora.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.jdbc.example.springbootaurora.model.Customer;
import software.amazon.jdbc.example.springbootaurora.repository.CustomerRepository;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CustomerService(CustomerRepository customerRepository, JdbcTemplate jdbcTemplate) {
        this.customerRepository = customerRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Customer createCustomer(String name) {
        Customer customer = new Customer(name);
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Optional<Customer> findCustomer(Long id) {
        return customerRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Customer> listCustomers() {
        return customerRepository.findAll();
    }

    public String getDatabaseVersion() {
        return jdbcTemplate.queryForObject("SELECT version()", String.class);
    }

    public void setSessionStrictWriter() {
        // This is a hypothetical way to set the attribute.
        // The AuroraAdvancedFeaturesPlugin needs to be able to detect this.
        // If SET SESSION is not detectable by the plugin's Statement.execute interceptor,
        // another mechanism (e.g., a special function call or a connection property) might be needed.
        jdbcTemplate.execute("SET SESSION aws_advanced_jdbc.strict_writer = 'true'");
    }

    public void clearSessionStrictWriter() {
        jdbcTemplate.execute("SET SESSION aws_advanced_jdbc.strict_writer = 'false'");
    }
}
