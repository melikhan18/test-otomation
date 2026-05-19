package com.devicefarm.auth.config;

import com.devicefarm.auth.domain.Product;
import com.devicefarm.auth.domain.ProductRepository;
import com.devicefarm.auth.domain.User;
import com.devicefarm.auth.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProductRepository products;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String adminUsername;
    private final String adminPassword;

    public DataSeeder(ProductRepository products, UserRepository users, PasswordEncoder encoder,
                      @Value("${app.admin.username}") String adminUsername,
                      @Value("${app.admin.password}") String adminPassword) {
        this.products = products;
        this.users = users;
        this.encoder = encoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        Product defaultProduct = products.findByCode("DEFAULT")
                .orElseGet(() -> products.save(new Product("DEFAULT", "Default Product")));

        if (!users.existsByUsername(adminUsername)) {
            User admin = new User(adminUsername, encoder.encode(adminPassword), defaultProduct.getId(), "ADMIN");
            users.save(admin);
            log.info("Seeded admin user '{}' under product DEFAULT", adminUsername);
        }
    }
}
