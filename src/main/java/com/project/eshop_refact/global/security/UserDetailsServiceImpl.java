package com.project.eshop_refact.global.security;

import com.project.eshop_refact.domain.user.User;
import com.project.eshop_refact.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security 사용자 정보 조회 서비스
 * 데이터베이스에서 회원을 조회하여 Security Context가 요구하는 인증 객체(UserDetails)로 변환합니다.
 */
@Service
@RequiredArgsConstructor
// OSIV(Open Session In View) 비활성화 환경에서, Security Filter 단의 엔티티 조회 시
// 영속성 컨텍스트 부재로 인한 지연 로딩(Lazy Loading) 예외를 방지하기 위해 읽기 전용 트랜잭션을 적용합니다.
@Transactional(readOnly = true)
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다"));

        return new UserDetailsImpl(user);
    }
}

