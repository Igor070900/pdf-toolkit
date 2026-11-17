package com.iedev.pdftoolkit.job.worker;

import com.iedev.pdftoolkit.config.WorkerProperties;
import com.iedev.pdftoolkit.converter.FileConversionService;
import com.iedev.pdftoolkit.job.service.JobService;
import com.iedev.pdftoolkit.s3.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Component
@RequiredArgsConstructor
public class ConversionWorker {

    private final JobService jobService;
    private final S3StorageService s3StorageService;
    private final FileConversionService fileConversionService;
    private final WorkerProperties workerProperties;

    public void processNextJob() {
        jobService.claimNextJob(workerProperties.getLeaseDuration()).ifPresent(
                job -> {
                    Path tempDirectory = null;
                    try {
                        tempDirectory = Files.createTempDirectory("pdf-toolkit-");
                        Path source = tempDirectory.resolve(job.getFileName());

                        s3StorageService.download(
                                job.getInputBucket(),
                                job.getInputObjectKey(),
                                source
                        );

                        String baseName = job.getFileName()
                                .substring(0, job.getFileName().lastIndexOf('.'));
                        Path destination = tempDirectory.resolve(baseName + ".pdf");

                        fileConversionService.convert(
                                job.getFileName(),
                                source,
                                destination
                        );

                        String outputObjectKey = "converted/" + job.getJobId() + "/" + baseName + ".pdf";

                        s3StorageService.upload(
                                job.getInputBucket(),
                                outputObjectKey,
                                destination
                        );

                        jobService.completeJob(job.getJobId());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } finally {
                        if (tempDirectory != null) {
                            try {
                                deleteTempDirectory(tempDirectory);
                            } catch (IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        }
                    }
                }
        );
    }

    private void deleteTempDirectory(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}