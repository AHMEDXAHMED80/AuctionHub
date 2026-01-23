package com.example.auctionhub.auctionhub.config;

import com.example.auctionhub.auctionhub.ratelimiter.RateLimitFilter;
import com.example.auctionhub.auctionhub.security.JwtAuthenticationFilter;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final RateLimitFilter rateLimitFilter;

    SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, @Lazy RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // CSRF Token handler to ensure token is loaded properly
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();// responsbile for
                                                                                                 // managing the CSRF
                                                                                                 // TOKEN
        requestHandler.setCsrfRequestAttributeName(null); // the name of the csrf token / -> usign defualt

        http
                .csrf(csrf -> csrf // to genetate the csrf token to protect from xss attacks
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                        .requireCsrfProtectionMatcher(request -> {
                            // Skip CSRF if Authorization header is present (API clients like Postman)
                            String authHeader = request.getHeader("Authorization");
                            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                                return false; // Skip CSRF for Bearer token requests
                            }
                            // Skip CSRF for auth endpoints, Stripe webhooks, and static resources
                            String path = request.getRequestURI();
                            if (path.startsWith("/api/auth/") ||
                                    path.startsWith("/api/stripe/webhook") ||
                                    path.equals("/") ||
                                    path.endsWith(".html") ||
                                    path.startsWith("/css/") ||
                                    path.startsWith("/js/") ||
                                    path.startsWith("/images/")) {
                                return false;
                            }
                            // Require CSRF for authenticated API requests with cookies
                            return true;
                        }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/login.html", "/register.html", "/logout.html",
                                "/favicon.ico")
                        .permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/stripe/webhook").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() { // for fortend // which source can make a request or get
                                                               // a response
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:8080"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter();
    }
}
