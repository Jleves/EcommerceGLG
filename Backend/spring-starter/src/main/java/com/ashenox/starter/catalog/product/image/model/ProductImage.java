package com.ashenox.starter.catalog.product.image.model;

import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.shared.persistence.EntidadAuditable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "product_images")
@Getter
@Setter
public class ProductImage extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "thumbnail_key", length = 500)
    private String thumbnailKey;

    @Column(name = "detail_key", length = 500)
    private String detailKey;

    @Column(nullable = false, length = 36)
    private String revision;

    @Column(nullable = false, length = 20)
    private String status;
}
