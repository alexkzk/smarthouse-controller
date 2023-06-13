package com.alexsoft.smarthouse.db.repository;

import com.alexsoft.smarthouse.db.entity.Indication;
import com.alexsoft.smarthouse.db.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository  extends JpaRepository<Visit, Integer> {

}
