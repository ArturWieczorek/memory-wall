package org.wall;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
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

  // Replaces the full-history index so we control what the feed/search see (no chain).
  @MockitoBean private WallIndex index;

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
  @DisplayName("GET /api/feed returns posts (with tip + pinned) served from the index")
  void feed() throws Exception {
    // A fresh timestamp so the pin is still within its window after the controller's forDisplay.
    when(index.allPosts())
        .thenReturn(
            List.of(
                new WallPost(
                    "alice",
                    "gm cardano",
                    Instant.now().toString(),
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
  @DisplayName("GET /api/feed paginates over the whole index (page 2 of 25 -> 5 posts)")
  void feedPaginates() throws Exception {
    List<WallPost> many =
        IntStream.range(0, 25)
            .mapToObj(
                i ->
                    new WallPost(
                        "u" + i, "m" + i, String.format("2026-06-30T10:%02d:00Z", i), "tx" + i))
            .toList();
    when(index.allPosts()).thenReturn(many);

    mvc.perform(get("/api/feed").param("limit", "20").param("page", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5));
  }

  @Test
  @DisplayName("GET /api/search matches author/message across ALL posts (full history)")
  void search() throws Exception {
    when(index.allPosts())
        .thenReturn(
            List.of(
                new WallPost("ada", "hello cardano", "2026-06-30T12:00:00Z", "txA"),
                new WallPost("bob", "bye", "2026-06-30T11:00:00Z", "txB")));

    mvc.perform(get("/api/search").param("q", "cardano"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].message").value("hello cardano"));
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
