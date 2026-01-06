package com.alexsoft.smarthouse.repository;

import com.alexsoft.smarthouse.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Integer> {

}
