package com.project.eshop_refact.stressTest;

import com.project.eshop_refact.domain.User;
import com.project.eshop_refact.domain.UserRoleEnum;
import com.project.eshop_refact.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local") // ★ 우리가 띄워둔 로컬 DB를 바라보게 세팅
public class UserSetupTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    @Rollback(false) // ★ 테스트가 끝나도 데이터가 날아가지 않고 DB에 영구 저장됨
    void insertDummyUsers() {
        String encodedPassword = passwordEncoder.encode("1234");

        for (int i = 1; i <= 500; i++) {
            String email = "user" + i + "@test.com";

            // 중복 저장 에러 방지
            if (userRepository.findByEmail(email).isEmpty()) {
                User user = new User(email, encodedPassword, "tester" + i, UserRoleEnum.USER);
                userRepository.save(user);
            }
        }
        System.out.println("500명 유저 DB 세팅 완료!");
    }
}