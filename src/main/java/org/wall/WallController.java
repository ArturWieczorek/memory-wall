package org.wall;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
  private final SubmitService submitService;
  private final Blocklist blocklist;
  private final WallProperties props;

  public WallController(
      FeedReader feedReader,
      PostTxBuilder txBuilder,
      SubmitService submitService,
      Blocklist blocklist,
      WallProperties props) {
    this.feedReader = feedReader;
    this.txBuilder = txBuilder;
    this.submitService = submitService;
    this.blocklist = blocklist;
    this.props = props;
  }

  /** Liveness check the UI polls to show an online/offline status light. */
  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }

  /**
   * Public config the UI needs to render (and explain) the fee/pin tier: whether it is on, the
   * thresholds, how many pins there are, and how long a pin lasts.
   */
  public record ConfigResponse(
      boolean feeEnabled,
      long minFeeLovelace,
      long pinFeeLovelace,
      int maxPinned,
      long pinDurationSeconds,
      List<String> palette) {}

  @GetMapping("/config")
  public ConfigResponse config() {
    return new ConfigResponse(
        props.feeEnabled(),
        props.minFeeLovelace(),
        props.pinFeeLovelace(),
        props.maxPinned(),
        props.pinDurationSeconds(),
        PinColors.PALETTE);
  }

  @GetMapping("/feed")
  public List<WallPost> feed(@RequestParam(defaultValue = "20") int limit) {
    // Display-side moderation: blocked posts are hidden from the feed (still permanent on-chain).
    return blocklist.filter(feedReader.recent(limit));
  }

  /**
   * Request to build a post tx: who pays/signs (address), the display name, the message, and an
   * optional tip (lovelace) to the operator's fee address when the fee tier is on.
   */
  public record BuildRequest(
      String address, String author, String message, Long tipLovelace, String color) {}

  /** Response carrying the unsigned transaction CBOR for the wallet to sign. */
  public record BuildResponse(String txCbor) {}

  @PostMapping("/posts/build")
  public ResponseEntity<?> build(@RequestBody BuildRequest req) {
    if (req.message() == null || req.message().isBlank()) {
      return ResponseEntity.badRequest().body("message must not be empty");
    }
    if (req.message().getBytes(StandardCharsets.UTF_8).length > props.maxMessageBytes()) {
      return ResponseEntity.badRequest()
          .body("message too long (max " + props.maxMessageBytes() + " bytes)");
    }
    if (req.address() == null || req.address().isBlank()) {
      return ResponseEntity.badRequest().body("address is required");
    }
    long tip = 0;
    if (props.feeEnabled()) {
      long minFee = props.minFeeLovelace();
      tip = req.tipLovelace() == null ? minFee : req.tipLovelace();
      if (tip < minFee) {
        return ResponseEntity.badRequest()
            .body("tip too low (min " + minFee + " lovelace to post)");
      }
    }
    String author = req.author() == null ? "" : req.author();
    String color = PinColors.normalize(req.color());
    WallPost post =
        new WallPost(author, req.message(), Instant.now().toString(), "", "", 0L, false, color);
    return ResponseEntity.ok(
        new BuildResponse(txBuilder.buildUnsignedHex(req.address(), post, tip)));
  }

  /** The signed-witness round-trip: the wallet returns a witness; we attach it and submit. */
  public record SubmitRequest(String txCbor, String witness) {}

  public record SubmitResponse(String txHash) {}

  @PostMapping("/posts/submit")
  public ResponseEntity<?> submit(@RequestBody SubmitRequest req) {
    if (req.txCbor() == null
        || req.txCbor().isBlank()
        || req.witness() == null
        || req.witness().isBlank()) {
      return ResponseEntity.badRequest().body("txCbor and witness are required");
    }
    if (req.txCbor().length() > props.maxTxChars() || req.witness().length() > props.maxTxChars()) {
      return ResponseEntity.badRequest().body("transaction too large");
    }
    return ResponseEntity.ok(new SubmitResponse(submitService.submit(req.txCbor(), req.witness())));
  }
}
