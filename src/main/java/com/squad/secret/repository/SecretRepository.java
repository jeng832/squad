package com.squad.secret.repository;

import com.squad.secret.domain.Secret;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SecretRepository extends JpaRepository<Secret, Long> {

    Optional<Secret> findByName(String name);
}
