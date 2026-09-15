package net.citizensnpcs.api.npc;

public interface NPC {

  MetadataStore data();

  enum Metadata {
    NAMEPLATE_VISIBLE("nameplate-visible");

    private final String key;

    Metadata(String key) {
      this.key = key;
    }

    public String getKey() {
      return key;
    }
  }
}
