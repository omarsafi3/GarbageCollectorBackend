package com.municipality.garbagecollectorbackend.config;

import com.municipality.garbagecollectorbackend.filter.JwtAuthenticationFilter;
import com.municipality.garbagecollectorbackend.service.UserService;
import com.municipality.garbagecollectorbackend.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http,
                                                       PasswordEncoder passwordEncoder,
                                                       UserService userService) throws Exception {
        AuthenticationManagerBuilder authBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authBuilder.userDetailsService(userService::loadUserByUsername)
                .passwordEncoder(passwordEncoder);
        return authBuilder.build();
    }

    // ✅ CORS Configuration Bean
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow Angular frontend (dev and production via Nginx)
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:4200", 
            "http://localhost:*",
            "http://localhost"
        ));

        // Allow all HTTP methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Allow all headers
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);

        // Expose Authorization header to frontend
        configuration.setExposedHeaders(Arrays.asList("Authorization"));

        // Cache preflight for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   UserService userService,
                                                   JwtUtil jwtUtil) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = new JwtAuthenticationFilter(userService, jwtUtil);

        http
                // ✅ Enable CORS with our configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ✅ Disable CSRF (not needed for stateless JWT)
                .csrf(csrf -> csrf.disable())

                // ✅ Configure authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        
                        // Swagger UI endpoints
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers("/swagger-resources/**", "/webjars/**").permitAll()

                        // Role-based endpoints
                        .requestMatchers("/api/employees/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/vehicles/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/departments/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/routes/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/bins/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/incidents/**").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/bins").hasAnyAuthority("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/users/**").hasAuthority("SUPER_ADMIN")

                        // All other requests need authentication
                        .anyRequest().authenticated()
                )

                // ✅ Stateless session management
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        // Add JWT filter before Spring Security's authentication filter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
