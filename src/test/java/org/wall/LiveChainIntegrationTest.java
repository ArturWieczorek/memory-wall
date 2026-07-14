package org.wall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Live-chain integration tests for the paths that genuinely need a backend - the unsigned-tx
 * builder ({@link PostTxBuilder}) and the feed reader ({@link BlockfrostFeedReader}). They are
 * excluded from the normal {@code test} task and run only via {@code ./gradlew integrationTest}.
 * Each SELF-SKIPS (JUnit assumption) unless a backend is configured, so this suite is safe to run
 * anywhere - with no env it reports "skipped", never "failed". No keys or funds are held by the
 * server; submitting a signed tx needs a real wallet signature and is exercised end-to-end through
 * the browser UI, not here.
 *
 * <p>To run against a real backend:
 *
 * <pre>
 *   WALL_IT_BACKEND_URL=https://cardano-preprod.blockfrost.io/api/v0/ \
 *   WALL_IT_PROJECT_ID=preprod... \
 *   WALL_IT_SENDER_ADDRESS=addr_test1... \
 *   ./gradlew integrationTest
 * </pre>
 */
@Tag("integration")
@DisplayName("Live chain integration (self-skips without WALL_IT_BACKEND_URL)")
class LiveChainIntegrationTest {

  private BackendService backend;
  private WallProperties props;

  @BeforeEach
  void setUp() {
    String url = System.getenv("WALL_IT_BACKEND_URL");
    assumeTrue(
        url != null && !url.isBlank(),
        "set WALL_IT_BACKEND_URL (+ WALL_IT_PROJECT_ID) to run live integration tests");
    String projectId = System.getenv().getOrDefault("WALL_IT_PROJECT_ID", "wall");
    backend = new BFBackendService(url, projectId);
    // Fee tier off (blank fee address); other fields take their record defaults.
    props =
        new WallProperties(
            url, projectId, null, null, null, null, null, null, "", 0L, 0L, 3, 604_800L);
  }

  @Test
  @DisplayName("PostTxBuilder builds a non-empty unsigned tx for a real funded address")
  void buildsUnsignedTx() {
    String sender = System.getenv("WALL_IT_SENDER_ADDRESS");
    assumeTrue(
        sender != null && !sender.isBlank(),
        "set WALL_IT_SENDER_ADDRESS (a funded testnet address) to build a real tx");
    PostTxBuilder builder = new PostTxBuilder(backend, props);
    WallPost post = WallPost.create("it", "integration-test message", Instant.now());

    String hex = builder.buildUnsignedHex(sender, post, 0);

    assertThat(hex).isNotBlank();
  }

  @Test
  @DisplayName("BlockfrostFeedReader can page the live feed without error")
  void readsFeedPage() {
    BlockfrostFeedReader reader = new BlockfrostFeedReader(backend, props);

    List<WallPost> page = reader.pageRaw(5, 1);

    assertThat(page).isNotNull(); // an empty wall is fine; we only assert the query round-trips
  }
}
