package org.wall;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.model.metadata.MetadataJSONContent;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads the feed by querying the backend for all transactions carrying our metadata label, and
 * parsing each one's JSON metadata into a {@link WallPost}. (Integration: needs a live backend; the
 * controller is unit-tested with a stubbed {@link FeedReader} instead.)
 */
@Component
public class BlockfrostFeedReader implements FeedReader {

  private final BackendService backend;

  public BlockfrostFeedReader(BackendService backend) {
    this.backend = backend;
  }

  @Override
  public List<WallPost> recent(int limit) {
    try {
      Result<List<MetadataJSONContent>> result =
          backend
              .getMetadataService()
              .getJSONMetadataByLabel(
                  BigInteger.valueOf(Wall.WALL_LABEL), limit, 1, OrderEnum.desc);
      if (!result.isSuccessful() || result.getValue() == null) {
        return List.of();
      }
      List<WallPost> posts = new ArrayList<>();
      for (MetadataJSONContent content : result.getValue()) {
        WallPost post = tryParse(content.getJsonMetadata());
        if (post != null) {
          posts.add(post);
        }
      }
      return Feed.newestFirst(posts);
    } catch (ApiException e) {
      throw new IllegalStateException("feed query failed", e);
    }
  }

  /** Parse one post from its JSON metadata, returning null if it is malformed. */
  private static WallPost tryParse(JsonNode node) {
    if (node == null) {
      return null;
    }
    try {
      String author = node.path("a").asText("");
      StringBuilder message = new StringBuilder();
      JsonNode m = node.path("m");
      if (m.isArray()) {
        m.forEach(chunk -> message.append(chunk.asText()));
      } else {
        message.append(m.asText(""));
      }
      String ts = node.path("ts").asText("");
      if (message.isEmpty() || ts.isBlank()) {
        return null;
      }
      return new WallPost(author, message.toString(), ts);
    } catch (RuntimeException e) {
      return null; // skip anything that does not look like one of our posts
    }
  }
}
