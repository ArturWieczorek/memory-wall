package org.wall;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** The feed hides blocklisted posts (display-side moderation), wired end-to-end through the API. */
@SpringBootTest(properties = {"wall.blocklist=spam", "wall.rate-limit.enabled=false"})
@AutoConfigureMockMvc
@DisplayName("Wall moderation")
class WallModerationTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private FeedReader feedReader;

  @Test
  @DisplayName("GET /api/feed hides posts matching the blocklist")
  void feedHidesBlocked() throws Exception {
    when(feedReader.recent(anyInt(), anyInt()))
        .thenReturn(
            List.of(
                new WallPost("alice", "clean post", "2026-06-30T12:01:00Z"),
                new WallPost("bob", "this is spam", "2026-06-30T12:00:00Z")));

    mvc.perform(get("/api/feed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].message").value("clean post"));
  }
}
