package com.msd.smartcart.infrastructure.adapter.in.http.security;

import com.msd.smartcart.domain.model.UserData;
import com.msd.smartcart.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserData userData = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found [email=%s]".formatted(email)
                ));

        // Único punto donde el dominio se traduce a Spring Security ✅
        return org.springframework.security.core.userdetails.User.builder()
                .username(userData.email())
                .password(userData.encodedPassword())
                .roles(userData.role())
                .build();
    }
}