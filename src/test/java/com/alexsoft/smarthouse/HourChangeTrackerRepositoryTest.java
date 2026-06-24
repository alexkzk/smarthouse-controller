package com.alexsoft.smarthouse;

import com.alexsoft.smarthouse.repository.HourChangeTrackerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HourChangeTrackerRepositoryTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private HourChangeTrackerRepository repository;

    @BeforeEach
    public void setup() {
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.executeUpdate()).thenReturn(1);
    }

    @Test
    public void testGetPreviousHourReturnsNullOnNoResult() {
        when(query.getSingleResult()).thenThrow(new jakarta.persistence.NoResultException("No result"));

        Integer result = repository.getPreviousHour();

        assertThat(result, is(nullValue()));
    }

    @Test
    public void testGetPreviousHourReturnsNullOnException() {
        when(query.getSingleResult()).thenThrow(new RuntimeException("Database down"));

        Integer result = repository.getPreviousHour();

        assertThat(result, is(nullValue()));
    }

    @Test
    public void testUpdatePreviousHourUpsertTrue() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        repository.updatePreviousHour(15, now, true);

        verify(entityManager).createNativeQuery("INSERT INTO main.hour_change_tracker (id, previous_hour, updated_at) values (1, :currentHour, :updated_at)");
        verify(query).setParameter("currentHour", 15);
        verify(query).setParameter("updated_at", now);
        verify(query).executeUpdate();
    }

    @Test
    public void testUpdatePreviousHourUpsertFalse() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        repository.updatePreviousHour(15, now, false);

        verify(entityManager).createNativeQuery("UPDATE main.hour_change_tracker SET previous_hour = :currentHour, updated_at = :updated_at where id = 1");
        verify(query).setParameter("currentHour", 15);
        verify(query).setParameter("updated_at", now);
        verify(query).executeUpdate();
    }
}
