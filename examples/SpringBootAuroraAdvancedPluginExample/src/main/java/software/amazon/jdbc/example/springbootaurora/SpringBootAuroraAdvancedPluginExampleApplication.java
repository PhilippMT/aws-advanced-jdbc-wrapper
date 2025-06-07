package software.amazon.jdbc.example.springbootaurora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringBootAuroraAdvancedPluginExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootAuroraAdvancedPluginExampleApplication.class, args);
    }

    // The @RestController and endpoint methods have been moved to CustomerController.java
    // The basic DataSource test previously here is effectively replaced by the /db-version
    // endpoint in CustomerController, which uses CustomerService and JdbcTemplate.
}
