package software.amazon.jdbc.example.springbootaurora.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.jdbc.example.springbootaurora.model.Customer;
import software.amazon.jdbc.example.springbootaurora.service.CustomerService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/") // Base path can be /api or similar
public class CustomerController {

    private final CustomerService customerService;

    @Autowired
    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/customers")
    public ResponseEntity<Customer> createCustomer(@RequestBody Map<String, String> payload) {
        String name = payload.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build(); // Or throw an exception
        }
        Customer customer = customerService.createCustomer(name);
        return ResponseEntity.status(HttpStatus.CREATED).body(customer);
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<Customer> findCustomer(@PathVariable Long id) {
        return customerService.findCustomer(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customers")
    public List<Customer> listCustomers() {
        return customerService.listCustomers();
    }

    @GetMapping("/db-version")
    public String getDbVersion() {
        return customerService.getDatabaseVersion();
    }

    @PostMapping("/session/strict-writer/enable")
    public ResponseEntity<String> enableStrictWriter() {
        try {
            customerService.setSessionStrictWriter();
            return ResponseEntity.ok("Strict-writer mode enabled for this session.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error enabling strict-writer: " + e.getMessage());
        }
    }

    @PostMapping("/session/strict-writer/disable")
    public ResponseEntity<String> disableStrictWriter() {
        try {
            customerService.clearSessionStrictWriter();
            return ResponseEntity.ok("Strict-writer mode disabled for this session.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error disabling strict-writer: " + e.getMessage());
        }
    }

    // Keep the root path in the main application class or move it here too
    @GetMapping("/")
    public String hello() {
        return "Hello from Spring Boot Aurora Advanced Plugin Example - Customer API!";
    }
}
