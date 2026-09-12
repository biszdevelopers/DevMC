package dev.bisz.players;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.bisz.players.internal.JsonProfileService;
import dev.bisz.storage.JsonDatabase;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RankTest {

  @Test
  void resolvesKnownRanksAndFallsBackToNone() {
    assertEquals(Rank.NONE, Rank.fromId(1));
    assertEquals(Rank.MOD, Rank.fromId(2));
    assertEquals(Rank.ADMIN, Rank.fromId(3));
    assertEquals(Rank.NONE, Rank.fromId(-1));
    assertEquals(Rank.NONE, Rank.fromId(99));
  }

  @Test
  void preservesLegacyAuthorityValues() {
    assertEquals(1, Rank.NONE.level());
    assertEquals(1, Rank.MOD.level());
    assertEquals(300, Rank.ADMIN.level());
    assertFalse(Rank.MOD.grantsOperator());
    assertTrue(Rank.ADMIN.grantsOperator());
  }

  @Test
  void persistsRankUpdatesToThePlayerProfile(@TempDir Path dataDirectory) {
    UUID playerId = UUID.randomUUID();
    try (JsonDatabase database = new JsonDatabase(dataDirectory)) {
      JsonProfileService profiles = new JsonProfileService(database);
      profiles.load(playerId, "Tester", "Tester").toCompletableFuture().join();
      PlayerProfile updated = profiles
        .updateRank(playerId, Rank.ADMIN.id())
        .toCompletableFuture()
        .join();

      assertEquals(Rank.ADMIN.id(), updated.permissionLevel());
      assertEquals(Rank.ADMIN, Rank.fromId(updated.permissionLevel()));
      Map<String, Object> stored = database.loadDataFromDataBase(
        "players.json"
      );
      Map<String, Object> player = database.objectValue(
        stored.get(playerId.toString())
      );
      assertEquals(Rank.ADMIN.id(), ((Number) player.get("rank")).longValue());
    }
  }
}
