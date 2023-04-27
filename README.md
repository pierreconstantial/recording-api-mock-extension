# recording-api-mock-extension

## About
JUnit 5 extensions based on WireMock that records real API requests and responses during the first run, and then use the recorded outcome as a mock in subsequent executions.

Wiremock: https://wiremock.org/

## Usage
Add the dependency to your `build.gradle`
```
dependencies {
  testImplementation 'org.pragmacode.test.utils:recording-api-mock-extension:1.0.0'
}
```

Register the extension in your test
BASE_URL is the URL of the real API.
```
@RegisterExtension
static RecordingMockServerExtension recordingMockServerExtension =
  new RecordingMockServerExtension(Config.builder().targetUrl(BASE_URL).port(8989).build());
```

Configure your test environment to use `localhost:8989` in place of the real API.
On the first run, the test will proxy the request to record the request and response.

By default, the recorded http request and response can be found under: `src/test/resources/wiremock/mappings/`. The files are prefixed with the method name of the test.
On the subsequent runs, the test will use the recorded response.
