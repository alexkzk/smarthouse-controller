package com.alexsoft.smarthouse.repository;

import com.alexsoft.smarthouse.entity.Appliance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import jakarta.transaction.Transactional;
import java.util.Optional;

public interface ApplianceRepository extends JpaRepository<Appliance, String> {

    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Appliance a WHERE a.code = :code")
    Optional<Appliance> findById(@Param("code") String code);



}
