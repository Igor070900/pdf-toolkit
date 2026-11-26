package com.iedev.pdftoolkit.converter.zip;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Validated
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "pdf-toolkit.zip")
public class ZipProcessingProperties {

    @Min(1)
    private int maxDepth = 5;

    @Min(1)
    private int maxFileCount = 1000;

    @Min(1)
    private long maxFileSizeBytes = 50 * 1024 * 1024;

    @Min(1)
    private long maxTotalSizeBytes = 500 * 1024 * 1024;

    @AssertTrue(message = "maxTotalSize must be greater than or equal to maxFileSize")
    public boolean isTotalSizeLimitValid() {
        return maxTotalSizeBytes >= maxFileSizeBytes;
    }
}