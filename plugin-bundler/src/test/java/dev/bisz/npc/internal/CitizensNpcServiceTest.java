package dev.bisz.npc.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import org.junit.jupiter.api.Test;

final class CitizensNpcServiceTest {

  @Test
  void configuresNameplateThroughPublicCitizensInterfaces() {
    TestNpc npc = new TestNpc();

    new CitizensNpcService().configureNameplate(npc, false);

    assertEquals("nameplate-visible", npc.key);
    assertEquals(false, npc.value);
  }

  private static final class TestNpc implements NPC {

    private String key;
    private Object value;

    @Override
    public MetadataStore data() {
      return new MetadataStore() {
        @Override
        public void setPersistent(String key, Object value) {
          TestNpc.this.key = key;
          TestNpc.this.value = value;
        }
      };
    }
  }
}
