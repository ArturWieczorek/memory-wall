package org.wall;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the backend (no real server) and exercises the API with MockMvc. The feed reader is
 * stubbed, so the test needs no chain - it verifies wiring + the endpoints' behaviour.
 */
@SpringBootTest(properties = "wall.rate-limit.enabled=false") // limiter tested separately
@AutoConfigureMockMvc
@DisplayName("Wall API")
class WallApiTest {

  @Autowired private MockMvc mvc;

  // Replaces BlockfrostFeedReader so we control what the feed returns.
  @MockitoBean private FeedReader feedReader;

  @Test
  @DisplayName("GET /api/health reports ok (the UI's status probe)")
  void health() throws Exception {
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  @DisplayName("CORS: a cross-origin request is allowed (header present)")
  void cors() throws Exception {
    mvc.perform(get("/api/health").header("Origin", "https://my-wall.example.com"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "*"));
  }

  @Test
  @DisplayName("GET /api/feed returns recent posts (with tip + pinned)")
  void feed() throws Exception {
    when(feedReader.recent(anyInt()))
        .thenReturn(
            List.of(
                new WallPost(
                    "alice",
                    "gm cardano",
                    "2026-06-30T12:00:00Z",
                    "tx123",
                    "addr_test1qalice",
                    5_000_000L,
                    true)));

    mvc.perform(get("/api/feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].author").value("alice"))
        .andExpect(jsonPath("$[0].message").value("gm cardano"))
        .andExpect(jsonPath("$[0].txHash").value("tx123"))
        .andExpect(jsonPath("$[0].address").value("addr_test1qalice"))
        .andExpect(jsonPath("$[0].tipLovelace").value(5_000_000))
        .andExpect(jsonPath("$[0].pinned").value(true));
  }

  @Test
  @DisplayName("GET /api/config reports the fee tier OFF by default (with pin defaults)")
  void configDefault() throws Exception {
    mvc.perform(get("/api/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feeEnabled").value(false))
        .andExpect(jsonPath("$.minFeeLovelace").value(0))
        .andExpect(jsonPath("$.maxPinned").value(3))
        .andExpect(jsonPath("$.pinDurationSeconds").value(604800));
  }

  @Test
  @DisplayName("POST /api/posts/build rejects an empty message")
  void buildRejectsEmpty() throws Exception {
    mvc.perform(
            post("/api/posts/build")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"addr_test1xyz\",\"author\":\"a\",\"message\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/posts/build rejects an over-long message (anti-DoS)")
  void buildRejectsTooLong() throws Exception {
    String huge = "x".repeat(5000); // default cap is 4096 bytes
    mvc.perform(
            post("/api/posts/build")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"address\":\"addr_test1xyz\",\"author\":\"a\",\"message\":\""
                        + huge
                        + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST /api/posts/submit rejects an over-large transaction (anti-DoS)")
  void submitRejectsTooLarge() throws Exception {
    String hugeCbor = "0".repeat(100_001); // default cap is 100000 chars
    mvc.perform(
            post("/api/posts/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"txCbor\":\"" + hugeCbor + "\",\"witness\":\"ab\"}"))
        .andExpect(status().isBadRequest());
  }
}
