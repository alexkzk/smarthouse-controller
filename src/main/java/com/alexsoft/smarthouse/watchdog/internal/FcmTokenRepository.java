package com.alexsoft.smarthouse.watchdog.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByToken(String token);

    @Query("SELECT t.token FROM FcmToken t")
    List<String> findAllTokens();

    void deleteByToken(String token);
}
