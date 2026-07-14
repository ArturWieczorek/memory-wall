package org.wall;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A durable {@link PostStore} backed by a single SQLite file. Opt-in: it is only wired in when
 * {@code wall.index.db-path} is set (see {@link WallConfig}).
 *
 * <p>Design choices for a small, dependency-light backend:
 *
 * <ul>
 *   <li><b>One table, plain JDBC.</b> No ORM, no connection pool - SQLite is a file, and the
 *       index's refresh is already single-threaded (synchronized), so a fresh connection per call
 *       is simple and safe.
 *   <li><b>{@code INSERT OR IGNORE} keyed by tx hash.</b> The transaction hash is the natural
 *       primary key (unique + immutable), so re-saving a post we already have is a harmless no-op -
 *       exactly the idempotency the {@link PostStore} contract promises.
 * </ul>
 *
 * <p>Roundtrip (save then load) is unit-tested against a temp-file database - no chain needed.
 */
public class SqlitePostStore implements PostStore {

  private static final String CREATE =
      "CREATE TABLE IF NOT EXISTS posts ("
          + "tx_hash TEXT PRIMARY KEY,"
          + "author TEXT NOT NULL,"
          + "message TEXT NOT NULL,"
          + "timestamp TEXT NOT NULL,"
          + "address TEXT NOT NULL,"
          + "tip_lovelace INTEGER NOT NULL,"
          + "pinned INTEGER NOT NULL,"
          + "color TEXT NOT NULL)";

  private static final String INSERT =
      "INSERT OR IGNORE INTO posts"
          + "(tx_hash,author,message,timestamp,address,tip_lovelace,pinned,color)"
          + " VALUES (?,?,?,?,?,?,?,?)";

  private static final String SELECT_ALL =
      "SELECT tx_hash,author,message,timestamp,address,tip_lovelace,pinned,color"
          + " FROM posts ORDER BY timestamp DESC";

  private final String url;

  public SqlitePostStore(String dbPath) {
    File file = new File(dbPath);
    File parent = file.getAbsoluteFile().getParentFile();
    if (parent != null) {
      parent.mkdirs(); // create the containing directory if needed
    }
    this.url = "jdbc:sqlite:" + file.getAbsolutePath();
    try (Connection c = connect();
        Statement s = c.createStatement()) {
      s.execute(CREATE);
    } catch (SQLException e) {
      throw new IllegalStateException("Could not open the index database at " + dbPath, e);
    }
  }

  @Override
  public List<WallPost> loadAll() {
    List<WallPost> out = new ArrayList<>();
    try (Connection c = connect();
        Statement s = c.createStatement();
        ResultSet rs = s.executeQuery(SELECT_ALL)) {
      while (rs.next()) {
        out.add(
            new WallPost(
                rs.getString("author"),
                rs.getString("message"),
                rs.getString("timestamp"),
                rs.getString("tx_hash"),
                rs.getString("address"),
                rs.getLong("tip_lovelace"),
                rs.getInt("pinned") != 0,
                rs.getString("color")));
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Could not read the index database", e);
    }
    return out;
  }

  @Override
  public void save(Collection<WallPost> posts) {
    if (posts.isEmpty()) {
      return;
    }
    try (Connection c = connect()) {
      c.setAutoCommit(false);
      try (PreparedStatement ps = c.prepareStatement(INSERT)) {
        for (WallPost p : posts) {
          if (p.txHash() == null || p.txHash().isBlank()) {
            continue; // the tx hash is the primary key - skip anything without one
          }
          ps.setString(1, p.txHash());
          ps.setString(2, p.author());
          ps.setString(3, p.message());
          ps.setString(4, p.timestamp());
          ps.setString(5, p.address());
          ps.setLong(6, p.tipLovelace());
          ps.setInt(7, p.pinned() ? 1 : 0);
          ps.setString(8, p.color());
          ps.addBatch();
        }
        ps.executeBatch();
        c.commit();
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Could not write to the index database", e);
    }
  }

  private Connection connect() throws SQLException {
    return DriverManager.getConnection(url);
  }
}
