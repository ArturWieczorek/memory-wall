package org.wall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The Memory Wall backend: builds the (unsigned) posting transaction for the browser wallet to
 * sign, and serves the feed of posts. Run with {@code ./gradlew run} (after a backend is
 * configured).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class WallApplication {

  public static void main(String[] args) {
    SpringApplication.run(WallApplication.class, args);
  }
}
