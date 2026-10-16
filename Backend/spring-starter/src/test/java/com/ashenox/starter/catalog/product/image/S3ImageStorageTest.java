package com.ashenox.starter.catalog.product.image;

import com.ashenox.starter.catalog.product.image.storage.S3ImageStorage;
import com.ashenox.starter.shared.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class S3ImageStorageTest {
    @Test
    void sendsWebpContentTypeAndOneDayCacheHeaderToConfiguredBucket() {
        AppProperties properties = new AppProperties();
        properties.getImages().setBucket("staging-bucket");
        S3Client client = mock(S3Client.class);
        new S3ImageStorage(client, properties).put("products/1/images/2/rev/thumb.webp", new byte[]{1, 2, 3});
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), org.mockito.ArgumentMatchers.any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("staging-bucket");
        assertThat(request.getValue().contentType()).isEqualTo("image/webp");
        assertThat(request.getValue().cacheControl()).isEqualTo("public, max-age=86400");
    }
}
