package rs.ac.soas.util;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

class HttpErrorsTest {
  @RestController
  static class Endpoint {
    @GetMapping("/rate")
    String rate() {
      return "ok";
    }

    @GetMapping("/missing")
    String missing() {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "private detail");
    }

    @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
    String json(@RequestBody Map<String, String> body) {
      return "ok";
    }
  }

  @Test
  void frameworkFailuresKeep404405415AndRequiredHeaders() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(new Endpoint())
            .setControllerAdvice(new GlobalErrors())
            .build();
    mvc.perform(post("/rate"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", "GET"))
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.message").value("Method Not Allowed"))
        .andExpect(jsonPath("$.trace").doesNotExist());
    mvc.perform(get("/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Not Found"));
    mvc.perform(post("/json").contentType(MediaType.APPLICATION_XML).content("<xml/>"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.status").value(415));
  }

  FeignException downstream(int status, String body) {
    var request =
        Request.create(
            Request.HttpMethod.GET,
            "http://bank/internal/bank/account",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null);
    return FeignException.errorStatus(
        "account",
        Response.builder()
            .request(request)
            .status(status)
            .reason("upstream")
            .headers(Map.of())
            .body(body, StandardCharsets.UTF_8)
            .build());
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 409, 422, 429})
  void downstreamClientStatusesAndBusinessMessagesSurviveFeign(int status) {
    var response =
        new GlobalErrors()
            .downstream(
                downstream(
                    status,
                    "{\"status\":"
                        + status
                        + ",\"error\":\"Error\",\"message\":\"User not found\"}"),
                new MockHttpServletRequest("GET", "/bank-accounts"));
    assertThat(response.getStatusCode().value()).isEqualTo(status);
    assertThat(response.getBody().message()).isEqualTo("User not found");
  }

  @Test
  void rawBodiesAndRemoteStackTracesAreNeverForwarded() {
    for (String body :
        java.util.List.of(
            "private stack trace",
            "{\"status\":404,\"error\":\"Error\",\"message\":\"private detail\",\"trace\":\"private"
                + " stack\"}")) {
      var response =
          new GlobalErrors().downstream(downstream(404, body), new MockHttpServletRequest());
      assertThat(response.getBody().message()).isEqualTo("Not Found");
    }
    var response =
        new GlobalErrors()
            .downstream(downstream(500, "private stack trace"), new MockHttpServletRequest());
    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody().message()).doesNotContain("private");
  }
}
