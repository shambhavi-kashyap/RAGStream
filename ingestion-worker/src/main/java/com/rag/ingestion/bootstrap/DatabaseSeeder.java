package com.rag.ingestion.bootstrap;

import com.rag.ingestion.domain.Tenant;
import com.rag.ingestion.domain.User;
import com.rag.ingestion.repository.TenantRepository;
import com.rag.ingestion.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (tenantRepository.findById("apple_inc").isEmpty()) {
            Tenant appleTenant = Tenant.builder()
                    .id("apple_inc")
                    .name("Apple Inc.")
                    .planTier("ENTERPRISE")
                    .isActive(true)
                    .build();
            tenantRepository.save(appleTenant);
            System.out.println("🌱 [SEEDER] Successfully created tenant: apple_inc");
        }

        if (userRepository.findByEmail("admin@apple.com").isEmpty()) {
            Tenant appleTenant = tenantRepository.findById("apple_inc").orElseThrow();
            
            User adminUser = User.builder()
                    .tenant(appleTenant)
                    .email("admin@apple.com")
                    .passwordHash(passwordEncoder.encode("admin123")) 
                    .role("ADMIN")
                    .build();
            userRepository.save(adminUser);
            System.out.println("🌱 [SEEDER] Successfully created user: admin@apple.com (Password: admin123)");
        }
    }
}