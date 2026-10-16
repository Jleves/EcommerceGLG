package com.ashenox.starter.catalog.product.image.model;

import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.shared.persistence.EntidadAuditable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_storage_operations")
@Getter
@Setter
public class ImageStorageOperation extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_id", nullable = false)
    private Long imageId;

    @Column(name = "thumbnail_key", nullable = false, length = 500)
    private String thumbnailKey;

    @Column(name = "detail_key", nullable = false, length = 500)
    private String detailKey;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_execution_at")
    private LocalDateTime nextExecutionAt;
}
