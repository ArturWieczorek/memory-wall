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
  @DisplayName("GET /api/feed returns recent posts")
  void feed() throws Exception {
    when(feedReader.recent(anyInt()))
        .thenReturn(List.of(new WallPost("alice", "gm cardano", "2026-06-30T12:00:00Z")));

    mvc.perform(get("/api/feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].author").value("alice"))
        .andExpect(jsonPath("$[0].message").value("gm cardano"));
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
