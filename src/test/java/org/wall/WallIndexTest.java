package org.wall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WallIndex (full-history cache)")
class WallIndexTest {

  private static WallPost p(String tx) {
    return new WallPost("a", "m-" + tx, "2026-06-30T10:00:00Z", tx);
  }

  @Test
  @DisplayName("ingests across pages until a short page (end of history), de-duping by tx hash")
  void initialIngest() {
    BlockfrostFeedReader reader = mock(BlockfrostFeedReader.class);
    when(reader.pageRaw(2, 1)).thenReturn(List.of(p("a"), p("b"))); // full page -> keep going
    when(reader.pageRaw(2, 2)).thenReturn(List.of(p("c"))); // short page -> end of history

    WallIndex index = new WallIndex(reader, 2, 100);
    index.refresh();

    assertThat(index.allPosts())
        .extracting(WallPost::txHash)
        .containsExactlyInAnyOrder("a", "b", "c");
    verify(reader).pageRaw(2, 1);
    verify(reader).pageRaw(2, 2);
    verify(reader, never()).pageRaw(2, 3); // stopped at the short page
  }

  @Test
  @DisplayName("a re-refresh whose first page is all-known stops after page 1, adds nothing")
  void incrementalStopsWhenCaughtUp() {
    BlockfrostFeedReader reader = mock(BlockfrostFeedReader.class);
    when(reader.pageRaw(2, 1)).thenReturn(List.of(p("a"), p("b")));
    when(reader.pageRaw(2, 2)).thenReturn(List.of(p("c")));
    WallIndex index = new WallIndex(reader, 2, 100);
    index.refresh(); // loads a, b, c

    clearInvocations(reader);
    index.refresh(); // page 1 = [a, b], both known -> nothing new -> stop

    assertThat(index.allPosts()).hasSize(3); // unchanged
    verify(reader).pageRaw(2, 1);
    verify(reader, never()).pageRaw(2, 2);
  }
}
