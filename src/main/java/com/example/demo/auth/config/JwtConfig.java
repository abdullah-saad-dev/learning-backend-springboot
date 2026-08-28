package com.example.demo.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.time.Clock;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    @Value("${app.jwt.secret}")
    private String secret;

    private SecretKey key(){
        return new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
    }
    @Bean
    JwtEncoder jwtEncoder(){
        return NimbusJwtEncoder.withSecretKey(key())
                .algorithm(MacAlgorithm.HS256)
                .build();
    }
    /**
     * The decoder validates "exp" and "nbf" against a clock of its own, and by default that is
     * Clock.systemUTC() - not the Clock bean JwtService mints tokens with. In production the two
     * agree and nothing shows. Against a test clock they do not: a token minted at the test's
     * instant is measured against real time and rejected as expired, which surfaces as a 500 from
     * the catch-all rather than as anything that names a clock.
     * <p>
     * So the validator is given the same Clock. "Time enters through an injected Clock" only holds
     * if the framework's own timekeeping is included in it.
     */
    @Bean
    JwtDecoder jwtDecoder(Clock clock){
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps));
        return decoder;
    }
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(){
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("role");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        authenticationConverter.setPrincipalClaimName("sub");
        return authenticationConverter;
    }
}
