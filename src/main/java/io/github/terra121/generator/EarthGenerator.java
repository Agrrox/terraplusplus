package io.github.terra121.generator;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import io.github.opencubicchunks.cubicchunks.api.util.Coords;
import io.github.opencubicchunks.cubicchunks.api.util.CubePos;
import io.github.opencubicchunks.cubicchunks.api.world.ICube;
import io.github.opencubicchunks.cubicchunks.api.worldgen.CubeGeneratorsRegistry;
import io.github.opencubicchunks.cubicchunks.api.worldgen.CubePrimer;
import io.github.opencubicchunks.cubicchunks.api.worldgen.populator.CubePopulatorEvent;
import io.github.opencubicchunks.cubicchunks.api.worldgen.populator.ICubicPopulator;
import io.github.opencubicchunks.cubicchunks.api.worldgen.populator.event.PopulateCubeEvent;
import io.github.opencubicchunks.cubicchunks.api.worldgen.structure.ICubicStructureGenerator;
import io.github.opencubicchunks.cubicchunks.api.worldgen.structure.event.InitCubicStructureGeneratorEvent;
import io.github.opencubicchunks.cubicchunks.cubicgen.BasicCubeGenerator;
import io.github.opencubicchunks.cubicchunks.cubicgen.common.biome.BiomeBlockReplacerConfig;
import io.github.opencubicchunks.cubicchunks.cubicgen.common.biome.CubicBiome;
import io.github.opencubicchunks.cubicchunks.cubicgen.common.biome.IBiomeBlockReplacer;
import io.github.opencubicchunks.cubicchunks.cubicgen.common.biome.IBiomeBlockReplacerProvider;
import io.github.opencubicchunks.cubicchunks.cubicgen.common.biome.OceanWaterReplacer;
import io.github.opencubicchunks.cubicchunks.cubicgen.common.biome.TerrainShapeReplacer;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.CustomGeneratorSettings;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.structure.CubicCaveGenerator;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.structure.CubicRavineGenerator;
import io.github.opencubicchunks.cubicchunks.cubicgen.customcubic.structure.feature.CubicStrongholdGenerator;
import io.github.terra121.dataset.osm.segment.Segment;
import io.github.terra121.generator.cache.CachedChunkData;
import io.github.terra121.generator.cache.ChunkDataLoader;
import io.github.terra121.generator.populate.IEarthPopulator;
import io.github.terra121.generator.populate.SnowPopulator;
import io.github.terra121.generator.populate.TreePopulator;
import io.github.terra121.projection.GeographicProjection;
import io.github.terra121.projection.OutOfProjectionBoundsException;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.InitMapGenEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.*;

public class EarthGenerator extends BasicCubeGenerator {
    public static final double WATEROFF_TRANSITION = -1.0d;

    public static boolean isNullIsland(int chunkX, int chunkZ) {
        return abs(chunkX) < 5 && abs(chunkZ) < 5;
    }

    public final BiomeProvider biomes;
    public final GeographicProjection projection;
    private final CustomGeneratorSettings cubiccfg;

    public final IBiomeBlockReplacer[][] biomeBlockReplacers;

    private final List<ICubicStructureGenerator> structureGenerators = new ArrayList<>();

    private final List<IEarthPopulator> populators = new ArrayList<>();
    private final Map<Biome, ICubicPopulator> biomePopulators = new IdentityHashMap<>();
    public final EarthGeneratorSettings cfg;

    public final GeneratorDatasets datasets;

    public final LoadingCache<ChunkPos, CompletableFuture<CachedChunkData>> cache;

    public EarthGenerator(World world) {
        super(world);

        this.cfg = new EarthGeneratorSettings(world.getWorldInfo().getGeneratorOptions());
        this.cubiccfg = this.cfg.getCustomCubic();
        this.projection = this.cfg.getProjection();

        this.biomes = world.getBiomeProvider(); //TODO: make this not order dependent

        this.datasets = new GeneratorDatasets(this.projection, this.cfg, world.getWorldInfo().isMapFeaturesEnabled());
        this.cache = CacheBuilder.newBuilder()
                .expireAfterAccess(5L, TimeUnit.MINUTES)
                .softValues()
                .build(new ChunkDataLoader(this.datasets));

        this.populators.add(TreePopulator.INSTANCE);

        //structures
        if (this.cubiccfg.caves) {
            InitCubicStructureGeneratorEvent caveEvent = new InitCubicStructureGeneratorEvent(InitMapGenEvent.EventType.CAVE, new CubicCaveGenerator());
            MinecraftForge.TERRAIN_GEN_BUS.post(caveEvent);
            this.structureGenerators.add(caveEvent.getNewGen());
        }
        if (this.cubiccfg.ravines) {
            InitCubicStructureGeneratorEvent ravineEvent = new InitCubicStructureGeneratorEvent(InitMapGenEvent.EventType.RAVINE, new CubicRavineGenerator(this.cubiccfg));
            MinecraftForge.TERRAIN_GEN_BUS.post(ravineEvent);
            this.structureGenerators.add(ravineEvent.getNewGen());
        }
        if (this.cubiccfg.strongholds) {
            InitCubicStructureGeneratorEvent strongholdsEvent = new InitCubicStructureGeneratorEvent(InitMapGenEvent.EventType.STRONGHOLD, new CubicStrongholdGenerator(this.cubiccfg));
            MinecraftForge.TERRAIN_GEN_BUS.post(strongholdsEvent);
            this.structureGenerators.add(strongholdsEvent.getNewGen());
        }

        for (Biome biome : ForgeRegistries.BIOMES) {
            CubicBiome cubicBiome = CubicBiome.getCubic(biome);
            this.biomePopulators.put(biome, cubicBiome.getDecorator(this.cubiccfg));
        }

        BiomeBlockReplacerConfig conf = this.cubiccfg.replacerConfig;
        Map<Biome, List<IBiomeBlockReplacer>> biomeBlockReplacers = new IdentityHashMap<>();
        for (Biome biome : ForgeRegistries.BIOMES) {
            CubicBiome cubicBiome = CubicBiome.getCubic(biome);
            Iterable<IBiomeBlockReplacerProvider> providers = cubicBiome.getReplacerProviders();
            List<IBiomeBlockReplacer> replacers = new ArrayList<>();
            for (IBiomeBlockReplacerProvider prov : providers) {
                replacers.add(prov.create(world, cubicBiome, conf));
            }

            //remove these replacers because they're redundant
            replacers.removeIf(replacer -> replacer instanceof TerrainShapeReplacer || replacer instanceof OceanWaterReplacer);

            biomeBlockReplacers.put(biome, replacers);
        }
        this.biomeBlockReplacers = new IBiomeBlockReplacer[biomeBlockReplacers.keySet().stream().mapToInt(Biome::getIdForBiome).max().orElse(0) + 1][];
        biomeBlockReplacers.forEach((biome, replacers) -> this.biomeBlockReplacers[Biome.getIdForBiome(biome)] = replacers.toArray(new IBiomeBlockReplacer[0]));
    }

    @Override
    public CubePrimer generateCube(int cubeX, int cubeY, int cubeZ) { //legacy compat method, no longer used
        throw new UnsupportedOperationException();
    }

    @Override
    public GeneratorReadyState pollAsyncCubeGenerator(int cubeX, int cubeY, int cubeZ) {
        CompletableFuture<CachedChunkData> future = this.cache.getUnchecked(new ChunkPos(cubeX, cubeZ));
        if (!future.isDone()) {
            return GeneratorReadyState.WAITING;
        } else if (future.isCompletedExceptionally()) {
            return GeneratorReadyState.FAIL;
        } else {
            return GeneratorReadyState.READY;
        }
    }

    @Override
    public Optional<CubePrimer> tryGenerateCube(int cubeX, int cubeY, int cubeZ, CubePrimer primer) {
        CompletableFuture<CachedChunkData> future = this.cache.getUnchecked(new ChunkPos(cubeX, cubeZ));
        if (!future.isDone() || future.isCompletedExceptionally()) {
            return Optional.empty();
        }

        CachedChunkData data = future.join();

        //build ground surfaces
        this.generateSurface(cubeX, cubeY, cubeZ, primer, data, this.world.getChunk(cubeX, cubeZ).getBiomeArray());

        //generate structures
        this.structureGenerators.forEach(gen -> gen.generate(this.world, primer, new CubePos(cubeX, cubeY, cubeZ)));

        if (data.intersectsSurface(cubeY)) { //render complex geometry onto cube surface
            //segments (roads, building outlines, streams, etc.)
            for (Segment s : data.segments()) {
                s.type.fillType().fill(data, primer, s, cubeX, cubeY, cubeZ);
            }
        }

        return Optional.of(primer);
    }

    private void generateSurface(int cubeX, int cubeY, int cubeZ, CubePrimer primer, CachedChunkData data, byte[] biomes) {
        IBlockState stone = Blocks.STONE.getDefaultState();
        IBlockState water = Blocks.WATER.getDefaultState();
        if (data.belowSurface(cubeY + 2)) { //below surface -> solid stone (padding of 2 cubes because some replacers might need it)
            //technically, i could reflectively get access to the primer's underlying char[] and use Arrays.fill(), because this
            // implementation causes 4096 calls to ObjectIntIdentityMap#get() when only 1 would be necessary...
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        primer.setBlockState(x, y, z, stone);
                    }
                }
            }
        } else if (data.aboveSurface(cubeY)) { //above surface -> air (no padding here, replacers don't normally affect anything above the surface)
            if (cubeY < 0) { //fill with water, we're in the ocean
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            primer.setBlockState(x, y, z, water);
                        }
                    }
                }
            }
        } else {
            double[] heights = data.heights();

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    double waterSurfaceHeight = max(heights[x * 16 + z] + WATEROFF_TRANSITION, 0.0d);
                    double height = data.heightWithWater(x, z);
                    double dx = x == 15 ? height - data.heightWithWater(x - 1, z) : data.heightWithWater(x + 1, z) - height;
                    double dz = z == 15 ? height - data.heightWithWater(x, z - 1) : data.heightWithWater(x, z + 1) - height;

                    int solidTop = min((int) ceil(height) - Coords.cubeToMinBlock(cubeY), 16);
                    int waterTop = min((int) ceil(waterSurfaceHeight) - Coords.cubeToMinBlock(cubeY), 16);

                    //if we're currently in the actual body of water, offset density by 1 to prevent underwater grass
                    double densityOffset = height < waterSurfaceHeight ? 1.0d : 0.0d;

                    int blockX = Coords.cubeToMinBlock(cubeX) + x;
                    int blockZ = Coords.cubeToMinBlock(cubeZ) + z;
                    IBiomeBlockReplacer[] replacers = this.biomeBlockReplacers[biomes[x * 16 + z] & 0xFF];
                    for (int y = 0; y < solidTop; y++) {
                        int blockY = Coords.cubeToMinBlock(cubeY) + y;
                        double density = height - blockY + densityOffset;
                        IBlockState state = stone;
                        for (IBiomeBlockReplacer replacer : replacers) {
                            state = replacer.getReplacedBlock(state, blockX, blockY, blockZ, dx, -1.0d, dz, density);
                        }

                        //calling this explicitly increases the likelihood of JIT inlining it
                        //(for reference: previously, CliffReplacer was manually added to each biome as the last replacer)
                        state = CliffReplacer.INSTANCE.getReplacedBlock(state, blockX, blockY, blockZ, dx, -1.0d, dz, density);

                        primer.setBlockState(x, y, z, state);
                    }

                    //fill water
                    for (int y = max(solidTop, 0); y < waterTop; y++) {
                        primer.setBlockState(x, y, z, water);
                    }
                }
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void populate(ICube cube) {
        if (!MinecraftForge.EVENT_BUS.post(new CubePopulatorEvent(this.world, cube))) {
            Random rand = Coords.coordsSeedRandom(this.world.getSeed(), cube.getX(), cube.getY(), cube.getZ());

            CachedChunkData data = this.cache.getUnchecked(cube.getCoords().chunkPos()).join();

            Biome biome = cube.getBiome(Coords.getCubeCenter(cube));

            if (this.cfg.settings.dynamicbaseheight) {
                this.cubiccfg.expectedBaseHeight = (float) data.heights[8 * 16 + 8];
            }

            MinecraftForge.EVENT_BUS.post(new PopulateCubeEvent.Pre(this.world, rand, cube.getX(), cube.getY(), cube.getZ(), false));

            if (data.intersectsSurface(cube.getY())) {
                for (IEarthPopulator populator : this.populators) {
                    populator.populate(this.world, rand, cube.getCoords(), biome, data);
                }
            }

            this.biomePopulators.get(biome).generate(this.world, rand, cube.getCoords(), biome);

            if (data.aboveSurface(cube.getY())) {
                SnowPopulator.INSTANCE.populate(this.world, rand, cube.getCoords(), biome, data);
            }

            MinecraftForge.EVENT_BUS.post(new PopulateCubeEvent.Post(this.world, rand, cube.getX(), cube.getY(), cube.getZ(), false));

            //other mod generators
            CubeGeneratorsRegistry.generateWorld(this.world, rand, cube.getCoords(), biome);
        }
    }

    @Override
    public BlockPos getClosestStructure(String name, BlockPos pos, boolean findUnexplored) {
        // eyes of ender are now compasses
        if ("Stronghold".equals(name)) {
            try {
                double[] vec = this.projection.vector(pos.getX(), pos.getZ(), 1, 0); //direction's to one meter north of here

                //normalize vector
                double mag = Math.sqrt(vec[0] * vec[0] + vec[1] * vec[1]);
                vec[0] /= mag;
                vec[1] /= mag;

                //project vector 100 blocks out to get "stronghold" position
                return new BlockPos((int) (pos.getX() + vec[0] * 100.0), pos.getY(), (int) (pos.getZ() + vec[1] * 100.0));
            } catch (OutOfProjectionBoundsException e) { //out of bounds, we can't really find where north is...
                //simply return center of world
                return BlockPos.ORIGIN;
            }
        }
        return null;
    }
}
