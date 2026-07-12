package org.wall;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Wires the Cardano backend bean from configuration. Constructing it opens no connection. */
@Configuration
public class WallConfig {

  @Bean
  public BackendService backendService(WallProperties props) {
    return new BFBackendService(props.backendUrl(), "wall");
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
