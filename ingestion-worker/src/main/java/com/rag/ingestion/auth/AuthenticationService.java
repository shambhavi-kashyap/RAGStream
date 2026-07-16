package com.rag.ingestion.auth;

import com.rag.ingestion.domain.Tenant;
import com.rag.ingestion.domain.User;
import com.rag.ingestion.repository.TenantRepository;
import com.rag.ingestion.repository.UserRepository;
import com.rag.ingestion.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationController.AuthResponse authenticate(AuthenticationController.AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        var user = userRepository.findByEmail(request.email()).orElseThrow();

        var jwtToken = jwtService.generateToken(user);
        
        return new AuthenticationController.AuthResponse(jwtToken);
    }

    public AuthenticationController.AuthResponse register(AuthenticationController.RegisterRequest request) {
        Tenant tenant = new Tenant();
        tenant.setName(request.organizationName()); 
        tenant.setPlanTier("STANDARD");
        
        tenant = tenantRepository.save(tenant); 

        var user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role("ADMIN") 
                .tenant(tenant) 
                .build();
        
        userRepository.save(user);

        var jwtToken = jwtService.generateToken(user);

        return new AuthenticationController.AuthResponse(jwtToken);
    }
}