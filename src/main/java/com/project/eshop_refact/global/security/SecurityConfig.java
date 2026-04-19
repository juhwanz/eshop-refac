package com.project.eshop_refact.global.security;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 글로벌 보안 설정
 * JWT를 활용한 Stateless 인증 아키텍처를 구성하며, 커스텀 예외 핸들러(401, 403) 및 API 인가 정책을 정의합니다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor // 생성자 주입
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint entryPoint;
    private final RestAccessDeniedHandler deniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * HTTP 요청에 대한 핵심 보안 필터 체인 구성
     * - 세션 관리: JWT 기반 인증을 위해 세션을 사용하지 않는 Stateless 정책 적용
     * - 예외 처리: REST API 환경에 맞춘 커스텀 인증/인가 예외 핸들러 등록
     * - 인가 정책: 인증/조회 API, 모니터링, 문서화 엔드포인트는 개방하고 상태 변경(POST, PATCH)은 권한 분리
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception{
        http
                .csrf(csrf->csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                )
                .authorizeHttpRequests(authz ->authz
                        .requestMatchers("/api/users/signup", "/api/users/login", "/api/users/reissue").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/orders/queue").authenticated()// 테스트 전용 허용 명시
                        //.requestMatchers(HttpMethod.POST, "/api/products").permitAll() // 테스트 시에만 사용
                        .requestMatchers("/actuator/**").permitAll() // 모니터링 허용
                        .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/products/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
