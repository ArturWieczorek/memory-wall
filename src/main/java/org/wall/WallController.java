package org.wall;

import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The wall API:
 *
 * <ul>
 *   <li>{@code GET /api/feed?limit=N} - recent posts, newest first.
 *   <li>{@code POST /api/posts/build} - build the UNSIGNED transaction for a post; the browser
 *       wallet signs and submits it.
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class WallController {

  private final FeedReader feedReader;
  private final PostTxBuilder txBuilder;

  public WallController(FeedReader feedReader, PostTxBuilder txBuilder) {
    this.feedReader = feedReader;
    this.txBuilder = txBuilder;
  }

  @GetMapping("/feed")
  public List<WallPost> feed(@RequestParam(defaultValue = "20") int limit) {
    return feedReader.recent(limit);
  }

  /** Request to build a post tx: who pays/signs (address), the display name, and the message. */
  public record BuildRequest(String address, String author, String message) {}

  /** Response carrying the unsigned transaction CBOR for the wallet to sign. */
  public record BuildResponse(String txCbor) {}

  @PostMapping("/posts/build")
  public ResponseEntity<?> build(@RequestBody BuildRequest req) {
    if (req.message() == null || req.message().isBlank()) {
      return ResponseEntity.badRequest().body("message must not be empty");
    }
    if (req.address() == null || req.address().isBlank()) {
      return ResponseEntity.badRequest().body("address is required");
    }
    String author = req.author() == null ? "" : req.author();
    WallPost post = WallPost.create(author, req.message(), Instant.now());
    return ResponseEntity.ok(new BuildResponse(txBuilder.buildUnsignedHex(req.address(), post)));
  }
}
