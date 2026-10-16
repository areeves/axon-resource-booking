package com.github.areeves.axon_resource_booking;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/docs", "/docs/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .addFilterAfter(new ActorIdentityFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder,
            @org.springframework.beans.factory.annotation.Value("${spring.security.user.name:admin}") String adminUsername,
            @org.springframework.beans.factory.annotation.Value("${spring.security.user.password:admin}") String adminPassword,
            @org.springframework.beans.factory.annotation.Value("${app.security.user-id:00000000-0000-0000-0000-000000000001}") String userId,
            @org.springframework.beans.factory.annotation.Value("${app.security.user-password:user}") String userPassword) {
        var admin = User.withUsername(adminUsername)
            .password(passwordEncoder.encode(adminPassword))
            .roles("ADMIN")
            .build();
        var user = User.withUsername(UUID.fromString(userId).toString())
            .password(passwordEncoder.encode(userPassword))
            .roles("USER")
            .build();
        return new InMemoryUserDetailsManager(admin, user);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static final class ActorIdentityFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String actorId = request.getHeader("X-User-Id");
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (actorId != null && authentication != null && authentication.isAuthenticated()
                    && authentication.getAuthorities().stream().noneMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"))) {
                try {
                    if (!UUID.fromString(actorId).equals(UUID.fromString(authentication.getName()))) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "X-User-Id must match the authenticated user");
                        return;
                    }
                } catch (IllegalArgumentException exception) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "X-User-Id must match the authenticated user");
                    return;
                }
            }
            chain.doFilter(request, response);
        }
    }
}
