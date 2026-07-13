package org.wall;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The API with the fee/pin tier switched ON (via test properties). We only exercise paths that stop
 * at validation (config + a too-low tip is rejected before the tx builder), so no chain is needed.
 */
@SpringBootTest(
    properties = {
      "wall.rate-limit.enabled=false",
      "wall.fee-address=addr_test1qfeeaddr",
      "wall.min-fee-lovelace=2000000",
      "wall.pin-fee-lovelace=5000000"
    })
@AutoConfigureMockMvc
@DisplayName("Wall API - fee tier on")
class WallFeeApiTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private FeedReader feedReader;

  @Test
  @DisplayName("GET /api/config reports the fee tier ON with its thresholds")
  void config() throws Exception {
    mvc.perform(get("/api/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feeEnabled").value(true))
        .andExpect(jsonPath("$.minFeeLovelace").value(2000000))
        .andExpect(jsonPath("$.pinFeeLovelace").value(5000000));
  }

  @Test
  @DisplayName("POST /api/posts/build rejects a tip below the minimum")
  void buildRejectsLowTip() throws Exception {
    mvc.perform(
            post("/api/posts/build")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"address\":\"addr_test1xyz\",\"author\":\"a\",\"message\":\"hi\","
                        + "\"tipLovelace\":1000000}"))
        .andExpect(status().isBadRequest());
  }
}
