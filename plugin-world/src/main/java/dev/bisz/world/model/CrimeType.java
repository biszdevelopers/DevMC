package dev.bisz.world.model;

/** The offences the police escalate against. */
public enum CrimeType {
  /** Drawing a weapon inside policed space. */
  WEAPON_DRAWN(1),
  /** Damaging a player inside policed space. */
  ASSAULT(3),
  /** Opening or taking from a container that is not yours. */
  THEFT(4),
  /** Breaking a protected block. */
  PROPERTY_DAMAGE(5),
  /** Carrying contraband into policed space. */
  CONTRABAND(2);

  private final int heat;

  CrimeType(int heat) {
    this.heat = heat;
  }

  /** How much heat one instance of this crime adds. */
  public int heat() {
    return heat;
  }
}
