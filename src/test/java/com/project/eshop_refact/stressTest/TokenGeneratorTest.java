package com.project.eshop_refact.stressTest;

import com.project.eshop_refact.config.JwtUtil;
import com.project.eshop_refact.domain.User;
import com.project.eshop_refact.domain.UserRoleEnum;
import com.project.eshop_refact.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileWriter;
import java.io.IOException;

@SpringBootTest
@ActiveProfiles("local")
class TokenGeneratorTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void generateTokensForLoadTest() throws IOException {
        FileWriter writer = new FileWriter("tokens.csv");
        writer.write("token\n"); // CSV 헤더

        // DB에 넣어둔 가상 유저의 이메일 형식에 맞춰 500개의 토큰 발급
        for (int i = 1; i <= 500; i++) {
            String email = "user" + i + "@test.com"; // 본인이 넣은 더미 유저 이메일 패턴
            String token = jwtUtil.createToken(email, UserRoleEnum.USER);
            writer.write(token + "\n");
        }
        writer.close();
        System.out.println("tokens.csv 파일 생성 완료!");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    @Rollback(false) // 테스트가 끝나도 DB에 데이터가 영구적으로 남도록 설정
    void insertDummyUsersForLoadTest() {
        String encodedPassword = passwordEncoder.encode("1234");

        // tokens.csv에 만든 이메일과 똑같이 1~500번 유저를 DB에 진짜로 저장
        for (int i = 1; i <= 500; i++) {
            User user = new User("user" + i + "@test.com", encodedPassword, "tester" + i, UserRoleEnum.USER);
            userRepository.save(user);
        }
        System.out.println("500명 유저 DB 등록 완료!");
    }
}