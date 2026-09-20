package dev.bisz.worldgen.worldgen;

/**
 * The deterministic climate field behind biome placement. It augments the
 * vanilla temperature and humidity with a large-scale gradient (so the map
 * spans the climate range) plus domain-warped fractal noise (so the biome
 * borders are organic contours rather than straight lines).
 *
 * <p>Pure and side-effect free so the coverage can be unit-tested.
 */
public final class ClimateField {

  private static final double ANGLE_TEMPERATURE = Math.PI * 0.25;
  private static final double ANGLE_HUMIDITY = Math.PI * 0.75;

  private static final long SALT_WARP_X = 0x1D_2C_3B_4AL;
  private static final long SALT_WARP_Z = 0x5E_6F_70_81L;
  private static final long SALT_TEMPERATURE = 0x51_1F_3A_7DL;
  private static final long SALT_HUMIDITY = 0x2B_9C_44_E1L;
  private static final long SALT_OCTAVE = 0x9E_37_79_B9L;

  private final long seed;
  private final double radius;
  private final double sweep;
  private final double scale;
  private final double warpBlocks;
  private final int octaves;

  public ClimateField(
    long seed,
    double radius,
    double sweep,
    double scale,
    double warpBlocks,
    int octaves
  ) {
    this.seed = seed;
    this.radius = Math.max(1.0, radius);
    this.sweep = sweep;
    this.scale = Math.max(0.00001, scale);
    this.warpBlocks = warpBlocks;
    this.octaves = Math.max(1, octaves);
  }

  /** Adjusted temperature at a column for a vanilla temperature input. */
  public double temperature(int x, int z, double vanilla) {
    return adjust(x, z, vanilla, ANGLE_TEMPERATURE, SALT_TEMPERATURE);
  }

  /** Adjusted humidity at a column for a vanilla humidity input. */
  public double humidity(int x, int z, double vanilla) {
    return adjust(x, z, vanilla, ANGLE_HUMIDITY, SALT_HUMIDITY);
  }

  private double adjust(
    int x,
    int z,
    double vanilla,
    double angle,
    long salt
  ) {
    double warpX =
      x + warpBlocks * fbm(seed ^ SALT_WARP_X, x * scale * 2, z * scale * 2);
    double warpZ =
      z + warpBlocks * fbm(seed ^ SALT_WARP_Z, x * scale * 2, z * scale * 2);
    double value =
      vanilla +
      sweep * gradient(warpX, warpZ, angle) +
      fbm(seed ^ salt, warpX * scale, warpZ * scale);
    return clamp(value);
  }

  private double gradient(double x, double z, double angle) {
    return (x * Math.cos(angle) + z * Math.sin(angle)) / radius;
  }

  /** Fractal value noise in roughly {@code [-1, 1]}. */
  private double fbm(long fieldSeed, double x, double z) {
    double sum = 0.0;
    double amplitude = 1.0;
    double normalizer = 0.0;
    double frequency = 1.0;
    for (int octave = 0; octave < octaves; octave++) {
      sum +=
        amplitude *
        valueNoise(
          fieldSeed + octave * SALT_OCTAVE,
          x * frequency,
          z * frequency
        );
      normalizer += amplitude;
      amplitude *= 0.5;
      frequency *= 2.0;
    }
    return sum / normalizer;
  }

  private static double valueNoise(long fieldSeed, double x, double z) {
    int xi = (int) Math.floor(x);
    int zi = (int) Math.floor(z);
    double xf = x - xi;
    double zf = z - zi;
    double u = xf * xf * (3 - 2 * xf);
    double w = zf * zf * (3 - 2 * zf);
    double c00 = hash(fieldSeed, xi, zi);
    double c10 = hash(fieldSeed, xi + 1, zi);
    double c01 = hash(fieldSeed, xi, zi + 1);
    double c11 = hash(fieldSeed, xi + 1, zi + 1);
    double a = c00 + u * (c10 - c00);
    double b = c01 + u * (c11 - c01);
    return a + w * (b - a);
  }

  private static double hash(long fieldSeed, int x, int z) {
    long h = fieldSeed;
    h ^= (long) x * 0x9E3779B97F4A7C15L;
    h ^= (long) z * 0xC2B2AE3D27D4EB4FL;
    h ^= h >>> 29;
    h *= 0xBF58476D1CE4E5B9L;
    h ^= h >>> 32;
    return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
  }

  private static double clamp(double value) {
    if (value < -1.0) return -1.0;
    if (value > 1.0) return 1.0;
    return value;
  }
}
