package com.alexsoft.smarthouse.core;

import com.alexsoft.smarthouse.core.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Integer> {

}
