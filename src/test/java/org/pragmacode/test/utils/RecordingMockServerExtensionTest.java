package org.pragmacode.test.utils;

import com.github.tomakehurst.wiremock.common.Notifier;
import com.github.tomakehurst.wiremock.http.HttpStatus;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.net.http.HttpResponse.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordingMockServerExtensionTest {
  @DisplayName("Recording should create a wiremock mapping file if none exists")
  @Test
  void testRecordingCreatesFile() throws NoSuchMethodException, IOException {
    Path tempDir = Files.createTempDirectory("test");
    Files.createDirectory(tempDir.resolve("mappings"));
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getTestMethod()).thenReturn(Optional.of(RecordingMockServerExtensionTest.class.getDeclaredMethod("testRecordingCreatesFile")));
    when(context.getTestClass()).thenReturn(Optional.of(RecordingMockServerExtensionTest.class));
    when(context.getDisplayName()).thenReturn("aTest");

    var extension = new RecordingMockServerExtension(RecordingMockServerExtension.Config.builder()
      .recordingLocation(tempDir.toString())
      .targetUrl("http://www.google.com")
      .build()
    );

    extension.beforeAll(null);
    assertNotNull(extension.getServer());
    assertTrue(extension.getServer().isRunning());
    extension.beforeEach(context);
    assertTrue(getFileNamesInDir(tempDir.resolve("mappings")).isEmpty());
    assertTrue(HttpStatus.isSuccess(makeHttpReq(extension.getServer().baseUrl())));
    extension.afterEach(context);
    assertDirContainsOnlyOneFileWhereName(tempDir.resolve("mappings"), name -> name.startsWith("testRecordingCreatesFile"));
    extension.afterAll(null);
    assertFalse(extension.getServer().isRunning());
  }

  @DisplayName("Recording should not create a file if one exists")
  @Test
  void testRecordingDoesntOverwriteFile() throws IOException, NoSuchMethodException {
    Path tempDir = Files.createTempDirectory("test");
    Files.createDirectory(tempDir.resolve("mappings"));
    Files.writeString(tempDir.resolve(Path.of("mappings", "testRecordingDoesntOverwriteFile-1234.json")), EXAMPLE_MAPPING_JSON);
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getTestMethod()).thenReturn(Optional.of(RecordingMockServerExtensionTest.class.getDeclaredMethod("testRecordingDoesntOverwriteFile")));
    when(context.getTestClass()).thenReturn(Optional.of(RecordingMockServerExtensionTest.class));
    when(context.getDisplayName()).thenReturn("aTest");

    var extension = new RecordingMockServerExtension(RecordingMockServerExtension.Config.builder()
      .recordingLocation(tempDir.toString())
      .targetUrl("http://www.google.com")
      .build()
    );

    extension.beforeAll(null);
    assertNotNull(extension.getServer());
    assertTrue(extension.getServer().isRunning());
    extension.beforeEach(context);
    assertDirContainsOnlyOneFileWhereName(tempDir.resolve("mappings"), name -> name.startsWith("testRecordingDoesntOverwriteFile"));
    assertTrue(HttpStatus.isSuccess(makeHttpReq(extension.getServer().baseUrl())));
    extension.afterEach(context);
    assertDirContainsOnlyOneFileWhereName(tempDir.resolve("mappings"), name -> name.startsWith("testRecordingDoesntOverwriteFile"));
    extension.afterAll(null);
    assertFalse(extension.getServer().isRunning());
  }

  @DisplayName("Recording should not create a file if target dir doesn't exist")
  @Test
  void testRecordingDoesntFailIfDirInvalid() throws IOException, NoSuchMethodException {
    Path tempDir = Files.createTempDirectory("test");
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getTestMethod()).thenReturn(Optional.of(RecordingMockServerExtensionTest.class.getDeclaredMethod("testRecordingDoesntOverwriteFile")));
    when(context.getTestClass()).thenReturn(Optional.of(RecordingMockServerExtensionTest.class));
    when(context.getDisplayName()).thenReturn("aTest");

    var extension = new RecordingMockServerExtension(RecordingMockServerExtension.Config.builder()
      .recordingLocation(tempDir.resolve("non-existing").toString())
      .targetUrl("http://www.google.com")
      .build()
    );

    extension.beforeAll(null);
    assertNotNull(extension.getServer());
    assertTrue(extension.getServer().isRunning());
    extension.beforeEach(context);
    assertTrue(getFileNamesInDir(tempDir).isEmpty());
    assertTrue(HttpStatus.isClientError(makeHttpReq(extension.getServer().baseUrl())));
    extension.afterEach(context);
    assertTrue(getFileNamesInDir(tempDir).isEmpty());
    extension.afterAll(null);
    assertFalse(extension.getServer().isRunning());
  }

  @DisplayName("Recording should not create a file if it's prohibited by settings")
  @Test
  void testRecordingDoesntCreateFile() throws IOException, NoSuchMethodException {
    Path tempDir = Files.createTempDirectory("test");
    Files.createDirectory(tempDir.resolve("mappings"));
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getTestMethod()).thenReturn(Optional.of(RecordingMockServerExtensionTest.class.getDeclaredMethod("testRecordingCreatesFile")));
    when(context.getTestClass()).thenReturn(Optional.of(RecordingMockServerExtensionTest.class));
    when(context.getDisplayName()).thenReturn("aTest");

    var extension = new RecordingMockServerExtension(RecordingMockServerExtension.Config.builder()
      .recordingLocation(tempDir.toString())
      .targetUrl("http://www.google.com")
      .skipRecordingForTestMethodNames(List.of("testRecordingCreatesFile"))
      .build()
    );

    extension.beforeAll(null);
    assertNotNull(extension.getServer());
    assertTrue(extension.getServer().isRunning());
    extension.beforeEach(context);
    assertTrue(getFileNamesInDir(tempDir.resolve("mappings")).isEmpty());
    assertFalse(HttpStatus.isSuccess(makeHttpReq(extension.getServer().baseUrl())));
    extension.afterEach(context);
    assertDirIsEmpty(tempDir.resolve("mappings"));
    extension.afterAll(null);
    assertFalse(extension.getServer().isRunning());
  }

  @DisplayName("It should be possible to use a custom notifier")
  @Test
  void testCustomNotifier() throws IOException, NoSuchMethodException {
    Path tempDir = Files.createTempDirectory("test");
    Files.createDirectory(tempDir.resolve("mappings"));
    ExtensionContext context = mock(ExtensionContext.class);
    when(context.getTestMethod()).thenReturn(Optional.of(RecordingMockServerExtensionTest.class.getDeclaredMethod("testRecordingCreatesFile")));
    when(context.getTestClass()).thenReturn(Optional.of(RecordingMockServerExtensionTest.class));
    when(context.getDisplayName()).thenReturn("aTest");
    Notifier notifier = mock(Notifier.class);

    var extension = new RecordingMockServerExtension(RecordingMockServerExtension.Config.builder()
      .recordingLocation(tempDir.toString())
      .targetUrl("http://www.google.com")
      .notifier(notifier)
      .build()
    );

    extension.beforeAll(null);
    assertNotNull(extension.getServer());
    assertTrue(extension.getServer().isRunning());
    extension.beforeEach(context);
    assertTrue(getFileNamesInDir(tempDir.resolve("mappings")).isEmpty());
    assertTrue(HttpStatus.isSuccess(makeHttpReq(extension.getServer().baseUrl())));
    extension.afterEach(context);
    assertDirContainsOnlyOneFileWhereName(tempDir.resolve("mappings"), name -> name.startsWith("testRecordingCreatesFile"));
    extension.afterAll(null);
    assertFalse(extension.getServer().isRunning());
    verify(notifier, times(2)).info(anyString());
  }

  @DisplayName("It shouldn't be a problem if the Mock-Server vanishes somehow")
  @Test
  void testExtensionCanHandleMockServerDeath() {
    var extension = new RecordingMockServerExtension(RecordingMockServerExtension.Config.builder().targetUrl("http://").build());

    extension.afterAll(null);
    extension.beforeAll(null);
    assertNotNull(extension.getServer());
    extension.getServer().stop();
    extension.afterAll(null);
  }

  @SneakyThrows
  private static int makeHttpReq(String baseUrl) {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(baseUrl))
      .build();
    return client.send(request, BodyHandlers.ofString()).statusCode();
  }

  @SneakyThrows
  private static void assertDirContainsOnlyOneFileWhereName(Path p, Predicate<String> nameCheck) {
    List<String> fileNamesInDir = getFileNamesInDir(p);
    assertEquals(1, fileNamesInDir.size());
    assertTrue(nameCheck.test(fileNamesInDir.get(0)));
  }

  @SneakyThrows
  private static void assertDirIsEmpty(Path p) {
    List<String> fileNamesInDir = getFileNamesInDir(p);
    assertEquals(0, fileNamesInDir.size(), "Following shouldn't exist: " + fileNamesInDir);
  }

  @SneakyThrows
  private static List<String> getFileNamesInDir(Path dir) {
    try (Stream<Path> files = Files.list(dir)) {
      return files.map(dir::relativize).map(Path::toString).collect(Collectors.toList());
    } catch (IOException e) {
      return List.of();
    }
  }

  private static final String EXAMPLE_MAPPING_JSON = """
      {
           "id":"5fded1bd-bfe3-4e43-9354-85fe0e794c78",
           "name":"",
           "request":{
              "url":"/",
              "method":"GET"
           },
           "response":{
              "status":200,
              "body":"<!doctype html><html><body>Google</body></html>"
           },
           "uuid":"5fded1bd-bfe3-4e43-9354-85fe0e794c78",
           "persistent":true,
           "metadata":{
              "test-class":"org.pragmacode.utils.RecordingMockServerExtensionTest",
              "test-method":"testRecordingCreatesFile",
              "test-name":"aTest"
           },
           "insertionIndex":1
        }
    """;
}