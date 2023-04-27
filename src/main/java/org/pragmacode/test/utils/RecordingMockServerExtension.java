package org.pragmacode.test.utils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.common.Metadata;
import com.github.tomakehurst.wiremock.common.Notifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.extension.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.client.WireMock.recordSpec;
import static com.github.tomakehurst.wiremock.common.Metadata.metadata;
import static com.github.tomakehurst.wiremock.common.SafeNames.makeSafeFileName;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

@Slf4j
public class RecordingMockServerExtension implements BeforeEachCallback, AfterEachCallback, BeforeAllCallback, AfterAllCallback {
  private static final String DEFAULT_RECORDING_LOCATION = "src/test/resources/wiremock";
  private static final String WIREMOCK_MAPPINGS = "/mappings";
  private static final String META_TEST_CLASS = "test-class";
  private static final String META_TEST_METHOD = "test-method";
  private static final String META_TEST_NAME = "test-name";
  public static final String UNKNOWN = "unknown";
  private WireMockServer mockServer;
  private final Config config;

  public RecordingMockServerExtension(Config config) {
    this.config = config;
  }

  public WireMockServer getServer() {
    return mockServer;
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    WireMockConfiguration cfg = WireMockConfiguration.options()
      .extensions(new ResponseTemplateTransformer(false))
      .notifier(defaultIfNull(config.notifier, new ConsoleNotifier(false)))
      .withRootDirectory(config.recordingLocation);
    mockServer = new WireMockServer(nonNull(config.port)? cfg.port(config.port) : cfg.dynamicPort());
    mockServer.start();
  }

  @Override
  public void afterAll(ExtensionContext context) {
    if (nonNull(mockServer) && mockServer.isRunning()) {
      mockServer.stop();
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    ifTestMethodPresent(context, testMethodName -> {
      if (recordingRequired(testMethodName, config.recordingLocation + WIREMOCK_MAPPINGS, config.skipRecordingForTestMethodNames)) {
        enableRecording();
      }
    });
  }

  @Override
  public void afterEach(ExtensionContext context) {
    ifTestMethodPresent(context, testMethodName -> {
      if (recordingRequired(testMethodName, config.recordingLocation + WIREMOCK_MAPPINGS, config.skipRecordingForTestMethodNames)) {
        persistRecording(createMetadata(context));
      }
    });
  }

  private void enableRecording() {
    mockServer.startRecording(recordSpec()
      .forTarget(config.targetUrl)
      .makeStubsPersistent(true)
      .extractTextBodiesOver(Long.MAX_VALUE) // don't extract bodies
      .extractBinaryBodiesOver(Long.MAX_VALUE)
      .build()
    );
  }

  private void persistRecording(Metadata metadata) {
    List<String> mappingFiles = new ArrayList<>();
    mockServer
      .stopRecording()
      .getStubMappings()
      .forEach(stubMapping -> {
        stubMapping.setMetadata(metadata);
        mockServer.editStubMapping(stubMapping);
        mappingFiles.add(makeSafeFileName(stubMapping));
      });

    moveMappings(metadata.getString(META_TEST_METHOD), mappingFiles, config.recordingLocation + WIREMOCK_MAPPINGS);
  }

  @Builder
  public static class Config {
    /**
     * record data from this url
     */
    private final String targetUrl;

    /**
     * use specific port instead of random for wiremock server
     */
    private final Integer port;

    /**
     * don't record for given test method names
     */
    @Builder.Default
    private final List<String> skipRecordingForTestMethodNames = List.of();

    /**
     * use this notifier instead of non-verbose console notifier
     */
    private final Notifier notifier;

    /**
     * save recorded data to this location
     */
    @Builder.Default
    private String recordingLocation = DEFAULT_RECORDING_LOCATION;
  }

  private static void ifTestMethodPresent(ExtensionContext context, Consumer<String> cb) {
    context.getTestMethod().map(Method::getName).ifPresent(cb);
  }

  private static boolean recordingRequired(String testMethodName, String mappingsLocation, List<String> skipRecordingForTestMethodNames) {
    if (skipRecordingForTestMethodNames.contains(testMethodName)) {
      return false;
    }
    try (Stream<Path> files = Files.walk(Paths.get(mappingsLocation))) {
      return files.noneMatch(path -> path.toFile().getName().startsWith(testMethodName + "-"));
    } catch (IOException e) {
      log.error("Can't check dir", e);
      return false;
    }
  }

  private static void moveMappings(String testMethodName, List<String> files, String mappingsLocation) {
    files.forEach(file -> {
        try {
          Files.move(
            Paths.get(mappingsLocation, file),
            Paths.get(mappingsLocation, format("%s-%s", testMethodName, file))
          );
        } catch (IOException e) {
          log.error(format("Cannot move: %s", file), e);
        }
      }
    );
  }

  private static Metadata createMetadata(ExtensionContext context) {
    return metadata()
      .attr(META_TEST_CLASS, context.getTestClass().map(Class::getName).orElse(UNKNOWN))
      .attr(META_TEST_METHOD, context.getTestMethod().map(Method::getName).orElse(UNKNOWN))
      .attr(META_TEST_NAME, context.getDisplayName())
      .build();
  }
}
