package com.devicefarm.auth.config;

import com.devicefarm.auth.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the default product/company/project plus the admin user on first boot.
 * Idempotent — safe to run on every startup.
 *
 * Tenancy note
 * ────────────
 * Runs AFTER Flyway's V3 backfill, so existing migrated users already have their
 * company_members rows. We're responsible for the freshly-created admin: it gets
 * platform_admin=true (cross-company access) plus OWNER membership in the default
 * company so the sidebar switcher has something to show.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProductRepository products;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final CompanyMemberRepository companyMembers;
    private final ProjectRepository projects;
    private final PasswordEncoder encoder;
    private final String adminUsername;
    private final String adminPassword;

    public DataSeeder(ProductRepository products, UserRepository users,
                      CompanyRepository companies, CompanyMemberRepository companyMembers,
                      ProjectRepository projects,
                      PasswordEncoder encoder,
                      @Value("${app.admin.username}") String adminUsername,
                      @Value("${app.admin.password}") String adminPassword) {
        this.products = products;
        this.users = users;
        this.companies = companies;
        this.companyMembers = companyMembers;
        this.projects = projects;
        this.encoder = encoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Product defaultProduct = products.findByCode("DEFAULT")
                .orElseGet(() -> products.save(new Product("DEFAULT", "Default Product")));

        // Default company tied to the legacy product (matches V3 backfill convention).
        Company defaultCompany = companies.findByLegacyProductId(defaultProduct.getId())
                .orElseGet(() -> {
                    Company c = new Company("default", "Default Company");
                    c.setLegacyProductId(defaultProduct.getId());
                    return companies.save(c);
                });

        // Default project under that company.
        projects.findByCompanyIdAndSlug(defaultCompany.getId(), "default")
                .orElseGet(() -> projects.save(new Project(defaultCompany.getId(), "default", "Default")));

        User admin;
        if (!users.existsByUsername(adminUsername)) {
            admin = new User(adminUsername, encoder.encode(adminPassword), defaultProduct.getId(), "ADMIN");
            admin.setPlatformAdmin(true);
            admin.setEmail("admin@local");
            admin = users.save(admin);
            log.info("Seeded admin user '{}'", adminUsername);
        } else {
            admin = users.findByUsername(adminUsername).orElseThrow();
            boolean dirty = false;
            if (!admin.isPlatformAdmin()) { admin.setPlatformAdmin(true); dirty = true; }
            if (admin.getEmail() == null) { admin.setEmail("admin@local"); dirty = true; }
            if (dirty) users.save(admin);
        }

        // OWNER membership in the default company — gives the switcher something to render.
        if (companyMembers.findByUserIdAndCompanyId(admin.getId(), defaultCompany.getId()).isEmpty()) {
            companyMembers.save(new CompanyMember(admin.getId(), defaultCompany.getId(), "OWNER"));
            log.info("Granted admin OWNER role in '{}'", defaultCompany.getSlug());
        }
    }
}
