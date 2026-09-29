package com.ashenox.starter.catalog.category.model;

import com.ashenox.starter.shared.persistence.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Category extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(name = "nombre_normalizado", nullable = false, unique = true, length = 160)
    private String nombreNormalizado;

    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false, length = 100)
    private String icono;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;
}
