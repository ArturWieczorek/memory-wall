package org.wall;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Wall metadata + chunking")
class WallTest {

  @Test
  @DisplayName("a short message is a single chunk")
  void shortMessageOneChunk() {
    assertThat(Wall.chunk("hello world")).containsExactly("hello world");
  }

  @Test
  @DisplayName("a long message splits into <=64-byte chunks that reassemble to the original")
  void longMessageChunks() {
    String message = "a".repeat(200);
    List<String> chunks = Wall.chunk(message);

    assertThat(chunks).hasSize(4); // 64 + 64 + 64 + 8
    for (String c : chunks) {
      assertThat(c.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
    }
    assertThat(String.join("", chunks)).isEqualTo(message);
  }

  @Test
  @DisplayName("never splits a multi-byte character across chunks")
  void multiByteSafe() {
    String message = "e".repeat(63) + "€€"; // 63 ASCII + two 3-byte euro signs
    List<String> chunks = Wall.chunk(message);
    for (String c : chunks) {
      assertThat(c.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(64);
    }
    assertThat(String.join("", chunks)).isEqualTo(message);
  }

  @Test
  @DisplayName("the serialized metadata embeds the author and message text")
  void metadataEmbedsPost() throws Exception {
    WallPost post = new WallPost("alice", "gm cardano", "2026-06-30T12:00:00Z");
    byte[] cbor = Wall.postMetadata(post).serialize();
    String asText = new String(cbor, StandardCharsets.ISO_8859_1);
    assertThat(cbor).isNotEmpty();
    assertThat(asText).contains("alice").contains("gm cardano");
  }
}
