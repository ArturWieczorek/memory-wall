package org.wall;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires the Cardano backend bean from configuration. Constructing it opens no connection. */
@Configuration
public class WallConfig {

  @Bean
  public BackendService backendService(WallProperties props) {
    return new BFBackendService(props.backendUrl(), props.backendProjectId());
  }

  /**
   * Where the full-history index keeps its copy of the wall. Default: in-memory only (re-ingests on
   * restart). Set {@code wall.index.db-path} (e.g. {@code data/wall-index.db}) to persist it to a
   * SQLite file, so a restart starts warm and only fetches new posts.
   */
  @Bean
  public PostStore postStore(@Value("${wall.index.db-path:}") String dbPath) {
    return (dbPath == null || dbPath.isBlank())
        ? new NoopPostStore()
        : new SqlitePostStore(dbPath.trim());
  }

  /**
   * Fail fast at startup if the fee tier is on but WALL_FEE_ADDRESS is not a real address -
   * otherwise every post's transaction build would blow up with an opaque error. Better to refuse
   * to start with a clear message than to accept posts that all fail.
   */
  @Bean
  public InitializingBean validateFeeAddress(WallProperties props) {
    return () -> {
      if (props.feeEnabled() && !Addresses.isValid(props.feeAddress())) {
        throw new IllegalStateException(
            "WALL_FEE_ADDRESS is not a valid Cardano address: '"
                + props.feeAddress()
                + "'. Use a full address you own (addr_test1... on preprod/preview, addr1... on"
                + " mainnet), or unset it to turn the fee/pin tier off.");
      }
    };
  }

  /**
   * Allow the hosted browser UI (a different origin) to call the API. Browsers block cross-origin
   * calls unless the server opts in with these CORS headers; the allowed origins are configurable
   * ("*" allows any, which is fine here because the API carries no cookies/credentials).
   */
  @Bean
  public WebMvcConfigurer corsConfigurer(WallProperties props) {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins(props.corsAllowedOrigins().toArray(new String[0]))
            .allowedMethods("GET", "POST", "OPTIONS");
      }
    };
  }
}
