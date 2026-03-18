package com.msd.smartcart.infrastructure.adapter.out.mongodb;

import com.msd.smartcart.domain.model.UserData;
import com.msd.smartcart.domain.port.out.UserRepository;
import com.msd.smartcart.infrastructure.adapter.out.mongodb.document.UserDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserRepositoryAdapter implements UserRepository {

    private final UserMongoRepository userMongoRepository;

    @Override
    public Optional<UserData> findByEmail(String email) {
        return userMongoRepository.findByEmail(email)
                .map(doc -> new UserData(
                        doc.getId(),
                        doc.getEmail(),
                        doc.getFullName(),
                        doc.getPassword(),
                        doc.getRole()
                ));
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMongoRepository.existsByEmail(email);
    }

    @Override
    public void save(UserData user) {
        userMongoRepository.save(UserDocument.builder()
                .email(user.email())
                .fullName(user.fullName())
                .password(user.encodedPassword())
                .role(user.role())
                .build()
        );
    }
}