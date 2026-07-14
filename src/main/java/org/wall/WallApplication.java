package org.wall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The Memory Wall backend: builds the (unsigned) posting transaction for the browser wallet to
 * sign, and serves the feed of posts. Run with {@code ./gradlew run} (after a backend is
 * configured). Scheduling is enabled so {@link WallIndex} can refresh the full-history cache.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class WallApplication {

  public static void main(String[] args) {
    SpringApplication.run(WallApplication.class, args);
  }
}
