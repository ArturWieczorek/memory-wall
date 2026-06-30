package org.wall;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code wall.*} settings from {@code application.yml}.
 *
 * @param backendUrl base URL of the Cardano backend (Blockfrost-compatible)
 */
@ConfigurationProperties(prefix = "wall")
public record WallProperties(String backendUrl) {}
