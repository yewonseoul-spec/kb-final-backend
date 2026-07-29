package org.scoula.security.account.domain;

import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.List;

@Getter
@Setter
public class CustomUser extends User {
    private MemberVO member;        // 실질적인 사용자 데이터

    public CustomUser(String username, String password,
                      Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
    }

    public CustomUser(MemberVO vo) {
        super(vo.getLoginId(), vo.getPassword(), makeAuthorities(vo.getRole()));
        this.member = vo;
    }

    private static Collection<? extends GrantedAuthority> makeAuthorities(String role) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

}
