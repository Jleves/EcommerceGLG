package com.ashenox.starter.catalog.characteristic.repository;

import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CharacteristicRepository extends JpaRepository<Characteristic, Long> {
    boolean existsByNombreNormalizado(String nombreNormalizado);
    boolean existsByNombreNormalizadoAndIdNot(String nombreNormalizado, Long id);

    List<Characteristic> findAllByOrderByNombreAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select characteristic from Characteristic characteristic where characteristic.id = :id")
    Optional<Characteristic> lockById(Long id);
}
