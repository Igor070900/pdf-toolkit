package com.iedev.pdftoolkit.job.worker;

import com.iedev.pdftoolkit.config.WorkerProperties;
import com.iedev.pdftoolkit.converter.ConversionResult;
import com.iedev.pdftoolkit.converter.ConvertedFileResult;
import com.iedev.pdftoolkit.converter.FileConversionService;
import com.iedev.pdftoolkit.job.entity.ConversionJobEntity;
import com.iedev.pdftoolkit.job.exception.LeaseLostException;
import com.iedev.pdftoolkit.job.service.JobService;
import com.iedev.pdftoolkit.job.worker.exception.PermanentProcessingException;
import com.iedev.pdftoolkit.job.worker.exception.RetryableProcessingException;
import com.iedev.pdftoolkit.kafka.event.SkippedFile;
import com.iedev.pdftoolkit.s3.storage.S3StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConversionWorkerTest {

    @Mock
    private JobService jobService;

    @Mock
    private S3StorageService s3StorageService;

    @Mock
    private FileConversionService fileConversionService;

    @Mock
    private WorkerProperties workerProperties;

    @Mock
    private ScheduledExecutorService workerScheduler;

    @Mock
    private ScheduledFuture<?> heartbeat;

    @InjectMocks
    private ConversionWorker conversionWorker;

    @Test
    public void shouldProcessJobSuccessfully() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        Path tempDir = Files.createTempDirectory("worker-test-");

        Duration leaseDuration = Duration.ofMinutes(15);
        Duration heartbeatInterval = Duration.ofMinutes(5);

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        AtomicReference<Path> downloadedFile = new AtomicReference<>();

        doAnswer(invocation -> {
            Path path = invocation.getArgument(2);
            downloadedFile.set(path);
            return null;
        }).when(s3StorageService).download(
                anyString(),
                anyString(),
                any(Path.class)
        );

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("sample.txt");
        when(job.getInputBucket()).thenReturn("input-bucket");
        when(job.getInputObjectKey()).thenReturn("input/sample.pdf");

        when(workerProperties.getLeaseDuration()).thenReturn(leaseDuration);
        when(workerProperties.getHeartbeatInterval()).thenReturn(heartbeatInterval);

        when(jobService.claimNextJob(leaseDuration))
                .thenReturn(Optional.of(job));

        doReturn(heartbeat).when(workerScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(heartbeatInterval.toMillis()),
                eq(heartbeatInterval.toMillis()),
                eq(TimeUnit.MILLISECONDS)
        );

        Path convertedPath = tempDir.resolve("document.pdf");

        when(fileConversionService.convert(
                anyString(),
                any(Path.class),
                any(Path.class),
                anyString(),
                anyString()
        )).thenReturn(new ConversionResult(
                List.of(new ConvertedFileResult(
                        convertedPath,
                        "input-bucket",
                        "converted/" + jobId + "/sample.pdf",
                        "document.pdf"
                )),
                List.of()
        ));
        conversionWorker.processNextJob();

        assertNotNull(downloadedFile.get());
        assertFalse(Files.exists(downloadedFile.get().getParent()));

        verify(s3StorageService).download(
                eq("input-bucket"),
                eq("input/sample.pdf"),
                any(Path.class)
        );

        verify(fileConversionService).convert(
                eq("sample.txt"),
                any(Path.class),
                any(Path.class),
                anyString(),
                anyString()
        );

        verify(s3StorageService).upload(
                eq("input-bucket"),
                eq("converted/" + jobId + "/sample.pdf"),
                any(Path.class)
        );

        verify(jobService).completeJob(jobId, lockToken);
        verify(heartbeat).cancel(false);
    }

    @Test
    public void shouldNotUploadOrCompleteJobWhenLeaseLostDuringConversion() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        Path tempDir = Files.createTempDirectory("worker-test-");

        Duration leaseDuration = Duration.ofMinutes(15);
        Duration heartbeatInterval = Duration.ofMinutes(5);

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("sample.txt");
        when(job.getInputBucket()).thenReturn("test-bucket");
        when(job.getInputObjectKey()).thenReturn("uploads/sample.txt");

        when(workerProperties.getLeaseDuration()).thenReturn(leaseDuration);
        when(workerProperties.getHeartbeatInterval()).thenReturn(heartbeatInterval);

        when(jobService.claimNextJob(leaseDuration))
                .thenReturn(Optional.of(job));

        AtomicReference<Runnable> heartbeatTask = new AtomicReference<>();

        doAnswer(invocation -> {
            heartbeatTask.set(invocation.getArgument(0));
            return heartbeat;
        }).when(workerScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(heartbeatInterval.toMillis()),
                eq(heartbeatInterval.toMillis()),
                eq(TimeUnit.MILLISECONDS)
        );

        Path convertedPath = tempDir.resolve("document.pdf");

        doAnswer(invocation -> {
            heartbeatTask.get().run();

            return new ConversionResult(
                    List.of(new ConvertedFileResult(
                            convertedPath,
                            "input-bucket",
                            "converted/test/document.pdf",
                            "document.pdf"
                    )),
                    List.of()
            );
        }).when(fileConversionService).convert(
                eq("sample.txt"),
                any(Path.class),
                any(Path.class),
                anyString(),
                anyString()
        );

        conversionWorker.processNextJob();

        verify(jobService).renewLease(jobId, lockToken, leaseDuration);
        verify(s3StorageService, never()).upload(
                anyString(),
                anyString(),
                any(Path.class)
        );
        verify(jobService, never()).completeJob(any(UUID.class), any(UUID.class));
        verify(heartbeat).cancel(false);
    }

    @Test
    public void shouldFailJobWhenPermanentProcessingErrorOccurs() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        Duration leaseDuration = Duration.ofMinutes(15);
        Duration heartbeatInterval = Duration.ofMinutes(5);

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("sample.txt");
        when(job.getInputBucket()).thenReturn("test-bucket");
        when(job.getInputObjectKey()).thenReturn("uploads/sample.txt");

        when(workerProperties.getLeaseDuration()).thenReturn(leaseDuration);
        when(workerProperties.getHeartbeatInterval()).thenReturn(heartbeatInterval);

        when(jobService.claimNextJob(leaseDuration))
                .thenReturn(Optional.of(job));

        doReturn(heartbeat).when(workerScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(heartbeatInterval.toMillis()),
                eq(heartbeatInterval.toMillis()),
                eq(TimeUnit.MILLISECONDS)
        );

        doThrow(new PermanentProcessingException("Invalid file"))
                .when(fileConversionService)
                .convert(eq("sample.txt"), any(Path.class), any(Path.class), anyString(), anyString());

        conversionWorker.processNextJob();

        verify(jobService).failJob(jobId, lockToken, "Invalid file");

        verify(jobService, never()).retryOrFailJob(
                any(UUID.class),
                any(UUID.class),
                anyString(),
                anyInt(),
                any(Duration.class),
                any(Duration.class)
        );

        verify(s3StorageService, never()).upload(
                anyString(),
                anyString(),
                any(Path.class)
        );

        verify(heartbeat).cancel(false);
    }

    @Test
    public void shouldRetryJobWhenTemporaryProcessingErrorOccurs() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        Duration leaseDuration = Duration.ofMinutes(15);
        Duration heartbeatInterval = Duration.ofMinutes(5);
        Duration retryInitialDelay = Duration.ofSeconds(30);
        Duration retryMaxDelay = Duration.ofMinutes(5);

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("sample.txt");
        when(job.getInputBucket()).thenReturn("test-bucket");
        when(job.getInputObjectKey()).thenReturn("uploads/sample.txt");

        when(workerProperties.getLeaseDuration()).thenReturn(leaseDuration);
        when(workerProperties.getHeartbeatInterval()).thenReturn(heartbeatInterval);
        when(workerProperties.getMaxAttempts()).thenReturn(3);
        when(workerProperties.getRetryInitialDelay()).thenReturn(retryInitialDelay);
        when(workerProperties.getRetryMaxDelay()).thenReturn(retryMaxDelay);

        when(jobService.claimNextJob(leaseDuration))
                .thenReturn(Optional.of(job));

        doReturn(heartbeat).when(workerScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(heartbeatInterval.toMillis()),
                eq(heartbeatInterval.toMillis()),
                eq(TimeUnit.MILLISECONDS)
        );

        doThrow(new RetryableProcessingException("Temporary S3 error", null))
                .when(s3StorageService)
                .download(eq("test-bucket"), eq("uploads/sample.txt"), any(Path.class));

        conversionWorker.processNextJob();

        verify(jobService).retryOrFailJob(
                jobId,
                lockToken,
                "Temporary S3 error",
                3,
                retryInitialDelay,
                retryMaxDelay
        );

        verify(jobService, never()).completeJob(any(UUID.class), any(UUID.class));
        verify(heartbeat).cancel(false);
    }

    @Test
    public void shouldHandleLeaseLostWhenRetryingJob() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        Duration leaseDuration = Duration.ofMinutes(15);
        Duration heartbeatInterval = Duration.ofMinutes(5);
        Duration retryInitialDelay = Duration.ofSeconds(30);
        Duration retryMaxDelay = Duration.ofMinutes(5);

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("sample.pdf");
        when(job.getInputBucket()).thenReturn("input-bucket");
        when(job.getInputObjectKey()).thenReturn("input/sample.pdf");

        when(workerProperties.getLeaseDuration()).thenReturn(leaseDuration);
        when(workerProperties.getHeartbeatInterval()).thenReturn(heartbeatInterval);
        when(workerProperties.getMaxAttempts()).thenReturn(3);
        when(workerProperties.getRetryInitialDelay()).thenReturn(retryInitialDelay);
        when(workerProperties.getRetryMaxDelay()).thenReturn(retryMaxDelay);

        when(jobService.claimNextJob(leaseDuration))
                .thenReturn(Optional.of(job));

        doReturn(heartbeat)
                .when(workerScheduler)
                .scheduleAtFixedRate(
                        any(Runnable.class),
                        eq(heartbeatInterval.toMillis()),
                        eq(heartbeatInterval.toMillis()),
                        eq(TimeUnit.MILLISECONDS)
                );

        doThrow(new RetryableProcessingException("Temporary S3 error", null))
                .when(s3StorageService)
                .download(eq("input-bucket"), eq("input/sample.pdf"), any(Path.class));

        doThrow(new LeaseLostException(jobId))
                .when(jobService)
                .retryOrFailJob(
                        jobId,
                        lockToken,
                        "Temporary S3 error",
                        3,
                        retryInitialDelay,
                        retryMaxDelay
                );

        conversionWorker.processNextJob();

        verify(jobService).retryOrFailJob(
                jobId,
                lockToken,
                "Temporary S3 error",
                3,
                retryInitialDelay,
                retryMaxDelay
        );

        verify(jobService, never()).completeJob(any(), any());
        verify(jobService, never()).failJob(any(), any(), any());
        verify(heartbeat).cancel(false);
    }

    @Test
    public void shouldFailJobWhenFileNameHasNoExtension() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("sample");

        when(workerProperties.getLeaseDuration()).thenReturn(Duration.ofMinutes(15));
        when(workerProperties.getHeartbeatInterval()).thenReturn(Duration.ofMinutes(5));

        when(jobService.claimNextJob(any(Duration.class)))
                .thenReturn(Optional.of(job));

        doReturn(heartbeat)
                .when(workerScheduler)
                .scheduleAtFixedRate(
                        any(Runnable.class),
                        anyLong(),
                        anyLong(),
                        eq(TimeUnit.MILLISECONDS)
                );

        conversionWorker.processNextJob();

        verify(jobService).failJob(jobId, lockToken, "Invalid file name");
        verify(s3StorageService, never()).download(anyString(), anyString(), any(Path.class));
        verify(fileConversionService, never())
                .convert(anyString(), any(Path.class), any(Path.class), anyString(), anyString());
        verify(heartbeat).cancel(false);
    }

    @Test
    public void shouldUploadAllConvertedFilesFromZip() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        Path tempDir = Files.createTempDirectory("worker-test-");

        Duration leaseDuration = Duration.ofMinutes(15);
        Duration heartbeatInterval = Duration.ofMinutes(5);

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("archive.zip");
        when(job.getInputBucket()).thenReturn("input-bucket");
        when(job.getInputObjectKey()).thenReturn("uploads/archive.zip");

        when(workerProperties.getLeaseDuration()).thenReturn(leaseDuration);
        when(workerProperties.getHeartbeatInterval()).thenReturn(heartbeatInterval);

        when(jobService.claimNextJob(leaseDuration))
                .thenReturn(Optional.of(job));

        doReturn(heartbeat).when(workerScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(heartbeatInterval.toMillis()),
                eq(heartbeatInterval.toMillis()),
                eq(TimeUnit.MILLISECONDS)
        );

        Path firstPdf = tempDir.resolve("document1.pdf");
        Path secondPdf = tempDir.resolve("document2.pdf");

        when(fileConversionService.convert(
                anyString(),
                any(Path.class),
                any(Path.class),
                anyString(),
                anyString()
        )).thenReturn(new ConversionResult(
                List.of(
                        new ConvertedFileResult(
                                firstPdf,
                                "input-bucket",
                                "converted/" + jobId + "/document1.pdf",
                                "document1.pdf"
                        ),
                        new ConvertedFileResult(
                                secondPdf,
                                "input-bucket",
                                "converted/" + jobId + "/document2.pdf",
                                "document2.pdf"
                        )
                ),
                List.of()
        ));

        conversionWorker.processNextJob();

        verify(s3StorageService).upload(
                eq("input-bucket"),
                eq("converted/" + jobId + "/document1.pdf"),
                eq(firstPdf)
        );

        verify(s3StorageService).upload(
                eq("input-bucket"),
                eq("converted/" + jobId + "/document2.pdf"),
                eq(secondPdf)
        );

        verify(s3StorageService, times(2)).upload(
                anyString(),
                anyString(),
                any(Path.class)
        );

        verify(jobService).completeJob(jobId, lockToken);
        verify(heartbeat).cancel(false);
    }

    @Test
    public void shouldCompleteJobWhenAllZipFilesAreSkipped() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        Duration leaseDuration = Duration.ofMinutes(15);
        Duration heartbeatInterval = Duration.ofMinutes(5);

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("archive.zip");
        when(job.getInputBucket()).thenReturn("input-bucket");
        when(job.getInputObjectKey()).thenReturn("uploads/archive.zip");

        when(workerProperties.getLeaseDuration()).thenReturn(leaseDuration);
        when(workerProperties.getHeartbeatInterval()).thenReturn(heartbeatInterval);

        when(jobService.claimNextJob(leaseDuration))
                .thenReturn(Optional.of(job));

        doReturn(heartbeat).when(workerScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(heartbeatInterval.toMillis()),
                eq(heartbeatInterval.toMillis()),
                eq(TimeUnit.MILLISECONDS)
        );

        when(fileConversionService.convert(
                anyString(),
                any(Path.class),
                any(Path.class),
                anyString(),
                anyString()
        )).thenReturn(new ConversionResult(
                List.of(),
                List.of(new SkippedFile(
                        "document.xyz",
                        "Unsupported file type"
                ))
        ));

        conversionWorker.processNextJob();

        verify(s3StorageService, never()).upload(
                anyString(),
                anyString(),
                any(Path.class)
        );

        verify(jobService).completeJob(jobId, lockToken);
        verify(heartbeat).cancel(false);
    }

    @Test
    public void shouldUploadConvertedFilesAndCompleteJobWhenSomeZipFilesAreSkipped() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        Duration leaseDuration = Duration.ofMinutes(15);
        Duration heartbeatInterval = Duration.ofMinutes(5);

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("archive.zip");
        when(job.getInputBucket()).thenReturn("input-bucket");
        when(job.getInputObjectKey()).thenReturn("uploads/archive.zip");

        when(workerProperties.getLeaseDuration()).thenReturn(leaseDuration);
        when(workerProperties.getHeartbeatInterval()).thenReturn(heartbeatInterval);

        when(jobService.claimNextJob(leaseDuration))
                .thenReturn(Optional.of(job));

        doReturn(heartbeat).when(workerScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(heartbeatInterval.toMillis()),
                eq(heartbeatInterval.toMillis()),
                eq(TimeUnit.MILLISECONDS)
        );

        Path convertedPdf = Path.of("document.pdf");

        when(fileConversionService.convert(
                anyString(),
                any(Path.class),
                any(Path.class),
                anyString(),
                anyString()
        )).thenReturn(new ConversionResult(
                List.of(new ConvertedFileResult(
                        convertedPdf,
                        "input-bucket",
                        "converted/" + jobId + "/document.pdf",
                        "document.pdf"
                )),
                List.of(new SkippedFile(
                        "document.xyz",
                        "Unsupported file type"
                ))
        ));

        conversionWorker.processNextJob();

        verify(s3StorageService).upload(
                "input-bucket",
                "converted/" + jobId + "/document.pdf",
                convertedPdf
        );

        verify(s3StorageService, times(1)).upload(
                anyString(),
                anyString(),
                any(Path.class)
        );

        verify(jobService).completeJob(jobId, lockToken);
        verify(heartbeat).cancel(false);
    }

    @Test
    public void shouldFailJobWhenUploadingConvertedFileFails() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        Duration leaseDuration = Duration.ofMinutes(15);
        Duration heartbeatInterval = Duration.ofMinutes(5);
        Duration retryInitialDelay = Duration.ofSeconds(30);
        Duration retryMaxDelay = Duration.ofMinutes(5);
        int maxAttempts = 3;

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("archive.zip");
        when(job.getInputBucket()).thenReturn("input-bucket");
        when(job.getInputObjectKey()).thenReturn("uploads/archive.zip");

        when(workerProperties.getLeaseDuration()).thenReturn(leaseDuration);
        when(workerProperties.getHeartbeatInterval()).thenReturn(heartbeatInterval);
        when(workerProperties.getMaxAttempts()).thenReturn(maxAttempts);
        when(workerProperties.getRetryInitialDelay()).thenReturn(retryInitialDelay);
        when(workerProperties.getRetryMaxDelay()).thenReturn(retryMaxDelay);

        when(jobService.claimNextJob(leaseDuration))
                .thenReturn(Optional.of(job));

        doReturn(heartbeat).when(workerScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(heartbeatInterval.toMillis()),
                eq(heartbeatInterval.toMillis()),
                eq(TimeUnit.MILLISECONDS)
        );

        Path convertedPdf = Path.of("document.pdf");
        String outputObjectKey = "converted/" + jobId + "/document.pdf";
        String errorMessage = "S3 upload failed";

        when(fileConversionService.convert(
                anyString(),
                any(Path.class),
                any(Path.class),
                anyString(),
                anyString()
        )).thenReturn(new ConversionResult(
                List.of(new ConvertedFileResult(
                        convertedPdf,
                        "input-bucket",
                        outputObjectKey,
                        "document.pdf"
                )),
                List.of()
        ));

        doThrow(new RetryableProcessingException(errorMessage, null))
                .when(s3StorageService)
                .upload("input-bucket", outputObjectKey, convertedPdf);

        conversionWorker.processNextJob();

        verify(s3StorageService).upload(
                "input-bucket",
                outputObjectKey,
                convertedPdf
        );

        verify(jobService).retryOrFailJob(
                jobId,
                lockToken,
                errorMessage,
                maxAttempts,
                retryInitialDelay,
                retryMaxDelay
        );

        verify(jobService, never()).completeJob(any(), any());
        verify(heartbeat).cancel(false);
    }

    @Test
    public void shouldRetryJobWhenSecondConvertedFileUploadFails() throws IOException {
        UUID jobId = UUID.randomUUID();
        UUID lockToken = UUID.randomUUID();

        Duration leaseDuration = Duration.ofMinutes(15);
        Duration heartbeatInterval = Duration.ofMinutes(5);
        Duration retryInitialDelay = Duration.ofSeconds(30);
        Duration retryMaxDelay = Duration.ofMinutes(5);
        int maxAttempts = 3;

        ConversionJobEntity job = mock(ConversionJobEntity.class);

        when(job.getJobId()).thenReturn(jobId);
        when(job.getLockToken()).thenReturn(lockToken);
        when(job.getFileName()).thenReturn("archive.zip");
        when(job.getInputBucket()).thenReturn("input-bucket");
        when(job.getInputObjectKey()).thenReturn("uploads/archive.zip");

        when(workerProperties.getLeaseDuration()).thenReturn(leaseDuration);
        when(workerProperties.getHeartbeatInterval()).thenReturn(heartbeatInterval);
        when(workerProperties.getMaxAttempts()).thenReturn(maxAttempts);
        when(workerProperties.getRetryInitialDelay()).thenReturn(retryInitialDelay);
        when(workerProperties.getRetryMaxDelay()).thenReturn(retryMaxDelay);

        when(jobService.claimNextJob(leaseDuration)).thenReturn(Optional.of(job));

        doReturn(heartbeat).when(workerScheduler).scheduleAtFixedRate(
                any(Runnable.class),
                eq(heartbeatInterval.toMillis()),
                eq(heartbeatInterval.toMillis()),
                eq(TimeUnit.MILLISECONDS)
        );

        Path firstPdf = Path.of("document1.pdf");
        Path secondPdf = Path.of("document2.pdf");

        String firstObjectKey = "converted/" + jobId + "/document1.pdf";
        String secondObjectKey = "converted/" + jobId + "/document2.pdf";
        String errorMessage = "S3 upload failed";

        when(fileConversionService.convert(
                anyString(),
                any(Path.class),
                any(Path.class),
                anyString(),
                anyString()
        )).thenReturn(new ConversionResult(
                List.of(
                        new ConvertedFileResult(
                                firstPdf,
                                "input-bucket",
                                firstObjectKey,
                                "document1.pdf"
                        ),
                        new ConvertedFileResult(
                                secondPdf,
                                "input-bucket",
                                secondObjectKey,
                                "document2.pdf"
                        )
                ),
                List.of()
        ));

        doNothing().when(s3StorageService)
                .upload("input-bucket", firstObjectKey, firstPdf);

        doThrow(new RetryableProcessingException(errorMessage, null))
                .when(s3StorageService)
                .upload("input-bucket", secondObjectKey, secondPdf);

        conversionWorker.processNextJob();

        verify(s3StorageService).upload(
                "input-bucket",
                firstObjectKey,
                firstPdf
        );

        verify(s3StorageService).upload(
                "input-bucket",
                secondObjectKey,
                secondPdf
        );

        verify(s3StorageService, times(2)).upload(
                anyString(),
                anyString(),
                any(Path.class)
        );

        verify(jobService).retryOrFailJob(
                jobId,
                lockToken,
                errorMessage,
                maxAttempts,
                retryInitialDelay,
                retryMaxDelay
        );

        verify(jobService, never()).completeJob(any(), any());
        verify(heartbeat).cancel(false);
    }
}