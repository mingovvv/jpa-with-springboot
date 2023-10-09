package com.example.jpa.service;

import com.example.jpa.domain.Member;
import com.example.jpa.repository.MemberRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
class memberServiceTest {

    @Autowired
    MemberService memberService;
    @Autowired
    MemberRepository memberRepository;

    @Test
    void 회원가입() {
        // [Given]
        Member member = new Member();
        member.setName("mingo");

        // [When]
        Long savedId = memberService.join(member);

        // [Then]
        // JPA에서 동일한 트랙잭션에서 동일한 Id를 지닌 객체는 영속성 컨텍스트에서 하나의 객체로 관리된다. 래퍼런스 비교(==) 가능
        assertEquals(member, memberService.findOne(savedId));
    }

    @Test()
    void 중복_회원_예외() {
        // [Given]
        Member member1 = new Member();
        member1.setName("mingo");

        Member member2 = new Member();
        member2.setName("mingo");

        // [When]
        memberService.join(member1);

        Assertions.assertThrows(IllegalStateException.class, () -> memberService.join(member2));
    }

}