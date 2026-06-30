package org.wall;

import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the Cardano backend bean from configuration. Constructing it opens no connection. */
@Configuration
public class WallConfig {

  @Bean
  public BackendService backendService(WallProperties props) {
    return new BFBackendService(props.backendUrl(), "wall");
  }
}
