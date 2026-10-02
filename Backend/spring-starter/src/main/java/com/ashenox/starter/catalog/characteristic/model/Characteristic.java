package com.ashenox.starter.catalog.characteristic.model;

import com.ashenox.starter.shared.persistence.EntidadAuditable;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "characteristics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class Characteristic extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(name = "nombre_normalizado", nullable = false, unique = true, length = 160)
    private String nombreNormalizado;
}
