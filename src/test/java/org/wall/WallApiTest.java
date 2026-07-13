package org.wall;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

  // Replaces the real tx builder so we can simulate a build failure without a chain.
  @MockitoBean private PostTxBuilder txBuilder;

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
    when(feedReader.recent(anyInt(), anyInt()))
        .thenReturn(
            List.of(
                new WallPost(
                    "alice",
                    "gm cardano",
                    "2026-06-30T12:00:00Z",
                    "tx123",
                    "addr_test1qalice",
                    5_000_000L,
                    true,
                    "mint")));

    mvc.perform(get("/api/feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].author").value("alice"))
        .andExpect(jsonPath("$[0].message").value("gm cardano"))
        .andExpect(jsonPath("$[0].txHash").value("tx123"))
        .andExpect(jsonPath("$[0].address").value("addr_test1qalice"))
        .andExpect(jsonPath("$[0].tipLovelace").value(5_000_000))
        .andExpect(jsonPath("$[0].pinned").value(true))
        .andExpect(jsonPath("$[0].color").value("mint"));
  }

  @Test
  @DisplayName("GET /api/config reports the fee tier OFF by default (with pin defaults)")
  void configDefault() throws Exception {
    mvc.perform(get("/api/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feeEnabled").value(false))
        .andExpect(jsonPath("$.minFeeLovelace").value(0))
        .andExpect(jsonPath("$.maxPinned").value(3))
        .andExpect(jsonPath("$.pinDurationSeconds").value(604800))
        .andExpect(jsonPath("$.palette[0]").value("rose"));
  }

  @Test
  @DisplayName("GET /api/feed forwards the page param to the reader")
  void feedForwardsPage() throws Exception {
    when(feedReader.recent(eq(20), eq(2)))
        .thenReturn(List.of(new WallPost("bob", "page two", "2026-06-30T11:00:00Z")));

    mvc.perform(get("/api/feed").param("limit", "20").param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].message").value("page two"));
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
  @DisplayName("POST /api/posts/build surfaces the build failure reason (not a bare 500)")
  void buildSurfacesReason() throws Exception {
    when(txBuilder.buildUnsignedHex(anyString(), any(), anyLong()))
        .thenThrow(
            new IllegalStateException(
                "failed to build post transaction", new RuntimeException("Not enough funds")));

    mvc.perform(
            post("/api/posts/build")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"address\":\"addr_test1xyz\",\"message\":\"hi\"}"))
        .andExpect(status().isBadGateway())
        .andExpect(content().string(containsString("Not enough funds")));
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
