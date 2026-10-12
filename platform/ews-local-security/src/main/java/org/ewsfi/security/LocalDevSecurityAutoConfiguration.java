package org.ewsfi.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Local-development HTTP Basic gate. {@code ANALYST} and {@code SUPERVISOR} are the only roles.
 * This is not an enterprise identity provider; replace it when one is chosen.
 */
@AutoConfiguration
@EnableConfigurationProperties(LocalSecurityProperties.class)
public class LocalDevSecurityAutoConfiguration {

    @Bean
    public SecurityFilterChain localDevSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasAnyRole("ANALYST", "SUPERVISOR"))
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    public UserDetailsService localDevUsers(LocalSecurityProperties properties) {
        return new InMemoryUserDetailsManager(
                User.withUsername("analyst")
                        .password("{noop}" + properties.getAnalystPassword())
                        .roles("ANALYST")
                        .build(),
                User.withUsername("supervisor")
                        .password("{noop}" + properties.getSupervisorPassword())
                        .roles("SUPERVISOR")
                        .build());
    }
}
