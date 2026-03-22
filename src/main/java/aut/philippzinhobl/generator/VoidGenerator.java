package aut.philippzinhobl.generator;

import org.bukkit.generator.ChunkGenerator;

public class VoidGenerator extends ChunkGenerator {
    @Override
    public boolean shouldGenerateNoise() { return false; }
    @Override
    public boolean shouldGenerateSurface() { return false; }
    @Override
    public boolean shouldGenerateBedrock() { return false; }
    @Override
    public boolean shouldGenerateCaves() { return false; }
}