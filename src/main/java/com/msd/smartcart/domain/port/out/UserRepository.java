package com.msd.smartcart.domain.port.out;

import com.msd.smartcart.domain.model.UserData;
import java.util.Optional;

// DEUDA TÉCNICA: UserRepository retorna UserDetails de Spring Security
// en el port de dominio. Aceptado pragmáticamente dado que SmartCart
// gestiona su propio auth. En arquitectura de microservicios real,
// esto sería responsabilidad de un servicio dedicado de identidad.
public interface UserRepository {
    Optional<UserData> findByEmail(String email);
    boolean existsByEmail(String email);
    void save(UserData user);
}