package org.wall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("WallIndex (full-history cache)")
class WallIndexTest {

  private static WallPost p(String tx) {
    return new WallPost("a", "m-" + tx, "2026-06-30T10:00:00Z", tx);
  }

  /** A default index over a stubbed reader with no persistence (the no-op store). */
  private static WallIndex index(BlockfrostFeedReader reader, int pageSize) {
    return new WallIndex(reader, new NoopPostStore(), pageSize, 100);
  }

  @Test
  @DisplayName("ingests across pages until a short page (end of history), de-duping by tx hash")
  void initialIngest() {
    BlockfrostFeedReader reader = mock(BlockfrostFeedReader.class);
    when(reader.pageRaw(2, 1)).thenReturn(List.of(p("a"), p("b"))); // full page -> keep going
    when(reader.pageRaw(2, 2)).thenReturn(List.of(p("c"))); // short page -> end of history

    WallIndex index = index(reader, 2);
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
    WallIndex index = index(reader, 2);
    index.refresh(); // loads a, b, c

    clearInvocations(reader);
    index.refresh(); // page 1 = [a, b], both known -> nothing new -> stop

    assertThat(index.allPosts()).hasSize(3); // unchanged
    verify(reader).pageRaw(2, 1);
    verify(reader, never()).pageRaw(2, 2);
  }

  @Test
  @DisplayName("warm start: seeds its cache from the store, so it does not begin empty")
  void seedsFromStore() {
    PostStore store = mock(PostStore.class);
    when(store.loadAll()).thenReturn(List.of(p("a"), p("b")));
    BlockfrostFeedReader reader = mock(BlockfrostFeedReader.class);

    WallIndex index = new WallIndex(reader, store, 100, 100);

    assertThat(index.allPosts()).extracting(WallPost::txHash).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  @DisplayName("saves only NEW posts to the store, and skips ones already seeded from it")
  void savesOnlyNewPosts() {
    // The store already holds "a"; the chain returns "a" (known) and "b" (new) on a short page.
    PostStore store = mock(PostStore.class);
    when(store.loadAll()).thenReturn(List.of(p("a")));
    BlockfrostFeedReader reader = mock(BlockfrostFeedReader.class);
    when(reader.pageRaw(2, 1)).thenReturn(List.of(p("a"), p("b"))); // full page -> read page 2
    when(reader.pageRaw(2, 2)).thenReturn(List.of()); // empty -> end of history

    WallIndex index = new WallIndex(reader, store, 2, 100);
    index.refresh();

    ArgumentCaptor<Collection<WallPost>> saved = ArgumentCaptor.forClass(Collection.class);
    verify(store).save(saved.capture());
    List<String> savedHashes = new ArrayList<>();
    for (WallPost wp : saved.getValue()) {
      savedHashes.add(wp.txHash());
    }
    assertThat(savedHashes).containsExactly("b"); // "a" was already known -> not re-saved
    assertThat(index.allPosts()).extracting(WallPost::txHash).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  @DisplayName("a store read error at startup does not crash - the index just starts empty")
  void toleratesStoreLoadFailure() {
    PostStore store = mock(PostStore.class);
    when(store.loadAll()).thenThrow(new IllegalStateException("db unavailable"));
    BlockfrostFeedReader reader = mock(BlockfrostFeedReader.class);

    WallIndex index = new WallIndex(reader, store, 100, 100);

    assertThat(index.allPosts()).isEmpty();
    verify(store, never()).save(anyCollection());
  }
}
