/*
 * Pore
 * Copyright (c) 2014-2016, Lapis <https://github.com/LapisBlue>
 *
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package blue.lapis.pore.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import blue.lapis.pore.Pore;
import blue.lapis.pore.converter.type.entity.EntityConverter;
import blue.lapis.pore.converter.type.world.BiomeConverter;
import blue.lapis.pore.converter.type.world.DifficultyConverter;
import blue.lapis.pore.converter.type.world.EnvironmentConverter;
import blue.lapis.pore.converter.type.world.GeneratorTypeConverter;
import blue.lapis.pore.converter.type.world.effect.EffectConverter;
import blue.lapis.pore.converter.type.world.effect.SoundConverter;
import blue.lapis.pore.converter.vector.LocationConverter;
import blue.lapis.pore.converter.vector.VectorConverter;
import blue.lapis.pore.converter.wrapper.WrapperConverter;
import blue.lapis.pore.impl.block.PoreBlock;
import blue.lapis.pore.impl.entity.PoreEntity;
import blue.lapis.pore.impl.entity.PoreFallingSand;
import blue.lapis.pore.impl.entity.PoreLivingEntity;
import blue.lapis.pore.impl.entity.PorePlayer;
import blue.lapis.pore.impl.entity.PoreLightningStrike;
import blue.lapis.pore.util.PoreCollections;
import blue.lapis.pore.util.PoreWrapper;

import com.flowpowered.math.vector.Vector3i;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.NotImplementedException;
import org.bukkit.BlockChangeDelegate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Difficulty;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.TreeType;
import org.bukkit.WorldBorder;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.CreatureType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import blue.lapis.pore.converter.type.material.ItemStackConverter;
import blue.lapis.pore.impl.entity.PoreItem;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.effect.particle.ParticleEffect;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.Living;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.world.World;
import org.spongepowered.api.world.explosion.Explosion;
import org.spongepowered.api.world.extent.Extent;
import org.spongepowered.api.world.weather.Weathers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PoreWorld extends PoreWrapper<World> implements org.bukkit.World {

    private static final float NATURAL_DROP_SCALAR = 0.85f;
    private static final float NATURAL_DROP_OFFSET = 0.15f;

    private static final long TICKS_PER_DAY = 24000L;

    private static final int DEFAULT_EFFECT_RADIUS = 64;

    public static PoreWorld of(Extent handle) {
        if (handle instanceof World) {
            return WrapperConverter.of(PoreWorld.class, handle);
        }
        throw new UnsupportedOperationException(); // TODO
    }

    protected PoreWorld(World handle) {
        super(handle);
    }

    @Override
    public Block getBlockAt(int x, int y, int z) {
        return PoreBlock.of(getHandle().getLocation(x, y, z));
    }

    @Override
    public Block getBlockAt(Location location) {
        return getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getBlockTypeIdAt(int x, int y, int z) {
        return getBlockAt(x, y, z).getTypeId();
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getBlockTypeIdAt(Location location) {
        return getBlockTypeIdAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public int getHighestBlockYAt(int x, int z) {
        for (int y = getMaxHeight(); y >= 0; y++) {
            //noinspection ConstantConditions
            if (getHandle().getBlock(x, y, z).getType() != BlockTypes.AIR) {
                return y;
            }
        }
        return 0;
    }

    @Override
    public int getHighestBlockYAt(Location location) {
        if (location.getWorld() == this) {
            return getHighestBlockYAt(location.getBlockX(), location.getBlockY());
        }
        throw new IllegalArgumentException(); //TODO: should an exception be thrown?
    }

    @Override
    public Block getHighestBlockAt(int x, int z) {
        return getBlockAt(x, getHighestBlockYAt(x, z), z);
    }

    @Override
    public Block getHighestBlockAt(Location location) {
        return getBlockAt(location.getBlockX(), getHighestBlockYAt(location), location.getBlockZ());
    }

    @Override
    public Chunk getChunkAt(int x, int z) {
        Optional<org.spongepowered.api.world.Chunk> chunk = getHandle().getChunk(new Vector3i(x, 0, z));
        return chunk.isPresent() ? PoreChunk.of(chunk.get()) : null;
    }

    @Override
    public Chunk getChunkAt(Location location) {
        return getChunkAt(location.getBlockX(), location.getBlockZ());
    }

    @Override
    public Chunk getChunkAt(Block block) {
        return getChunkAt(block.getLocation().getBlockX(), block.getLocation().getBlockZ());
    }

    @Override
    public Chunk[] getLoadedChunks() {
        List<Chunk> chunks = new ArrayList<>();
        for (org.spongepowered.api.world.Chunk chunk : getHandle().getLoadedChunks()) {
            chunks.add(PoreChunk.of(chunk));
        }

        return chunks.toArray(new Chunk[chunks.size()]);
    }

    @Override
    public boolean isChunkLoaded(Chunk chunk) {
        return chunk.isLoaded();
    }

    @Override
    public boolean isChunkLoaded(int x, int z) {
        return getHandle().getChunk(new Vector3i(x, 0, z)).isPresent();
    }

    @Override
    public boolean isChunkInUse(int x, int z) {
        // Sponge does not expose a direct way to check if a chunk is "in use".
        // Approximate by checking whether the chunk is loaded and contains any
        // entities. This roughly matches Bukkit behaviour where a chunk is
        // considered in use when it has activity.
        Chunk chunk = getChunkAt(x, z);
        return chunk != null && chunk.isLoaded() && chunk.getEntities().length > 0;
    }

    @Override
    public void loadChunk(Chunk chunk) {
        loadChunk(chunk.getX(), chunk.getZ());
    }

    @Override
    public void loadChunk(int x, int z) {
        loadChunk(x, z, true);
    }

    @Override
    public boolean loadChunk(int x, int z, boolean generate) {
        return getHandle().loadChunk(new Vector3i(x, 0, z), generate).isPresent();
    }

    @Override
    public boolean unloadChunk(Chunk chunk) {
        return chunk.unload();
    }

    @Override
    public boolean unloadChunk(int x, int z) {
        return unloadChunk(x, z, true);
    }

    @Override
    public boolean unloadChunk(int x, int z, boolean save) {
        return unloadChunk(x, z, save, false);
    }

    @Override
    public boolean unloadChunk(int x, int z, boolean save, boolean safe) {
        return getChunkAt(x, z).unload(save, safe);
    }

    @Override
    public boolean unloadChunkRequest(int x, int z) {
        // Bukkit's unloadChunkRequest schedules an unload. Sponge only exposes
        // direct unloading, so we approximate by immediately unloading the
        // chunk with default save behaviour.
        return unloadChunk(x, z, true);
    }

    @Override
    public boolean unloadChunkRequest(int x, int z, boolean safe) {
        // Same approximation as above while respecting the safe flag.
        return unloadChunk(x, z, true, safe);
    }


    @Override
    public boolean regenerateChunk(int x, int z) {
        // Sponge offers a regenerateChunk method taking a cause, but it is not
        // available in all implementations. Approximate by unloading then
        // loading the chunk again with generation enabled.
        unloadChunk(x, z, false, false);
        return loadChunk(x, z, true);
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean refreshChunk(int x, int z) {
        // Bukkit refreshes a chunk for players. Sponge has no direct method, so
        // approximate by unloading and reloading the chunk.
        unloadChunk(x, z, true, false);
        return loadChunk(x, z, false);
    }

    @Override
    public Item dropItem(Location location, ItemStack item) {
        //noinspection ConstantConditions
        Optional<org.spongepowered.api.entity.Entity> created =
                getHandle().createEntity(EntityTypes.ITEM, VectorConverter.create3d(location));
        if (!created.isPresent()) {
            return null;
        }

        org.spongepowered.api.entity.Item drop = (org.spongepowered.api.entity.Item) created.get();
        // Sponge does not expose a direct pickup delay, so it is ignored.
        drop.offer(Keys.REPRESENTED_ITEM, ItemStackConverter.of(item).createSnapshot());
        getHandle().spawnEntity(drop, Cause.of(NamedCause.source(Pore.getPlugin())));
        return PoreItem.of(drop);
    }

    @Override
    public Item dropItemNaturally(Location location, ItemStack item) {
        // this is how it's expected to behave from what I can understand
        return dropItem(
                location.clone().add(
                        Math.random() * NATURAL_DROP_SCALAR + NATURAL_DROP_OFFSET,
                        Math.random() * NATURAL_DROP_SCALAR + NATURAL_DROP_OFFSET,
                        Math.random() * NATURAL_DROP_SCALAR + NATURAL_DROP_OFFSET
                ), item
        );
    }

    @Override
    public Arrow spawnArrow(Location location, Vector direction, float speed, float spread) {
        checkNotNull(location, "Location cannot be null");
        checkNotNull(direction, "Direction cannot be null");
        Entity spawned = spawnEntity(location, EntityType.ARROW);
        assert spawned instanceof Arrow; // basic sanity check
        Arrow arrow = (Arrow) spawned;
        arrow.setVelocity(VectorConverter.getUnitVector(direction).multiply(speed)); // I know, it's weird
        //TODO: spread
        return arrow;
    }

    @Override
    public boolean generateTree(Location location, TreeType type) {
        // Sponge lacks a direct tree generation helper. Approximate by placing
        // a very simple tree structure using normal block operations.
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Basic trunk
        for (int i = 0; i < 5; i++) {
            getBlockAt(x, y + i, z).setType(Material.LOG);
        }

        // Simple leaves cube
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 5; dy++) {
                    getBlockAt(x + dx, y + dy, z + dz).setType(Material.LEAVES);
                }
            }
        }

        return true;
    }

    @Override
    public boolean generateTree(Location loc, TreeType type, BlockChangeDelegate delegate) {
        // Bukkit passes a BlockChangeDelegate to modify blocks. Sponge doesn't
        // provide a similar callback, so we simply ignore the delegate and
        // generate the tree directly.
        return generateTree(loc, type);
    }

    @Override
    public Entity spawnEntity(Location loc, EntityType type) {
        org.spongepowered.api.entity.Entity entity = getHandle().createEntity(EntityConverter.of(type),
                VectorConverter.create3d(loc)).orElse(null);
        if (entity == null) {
            return null;
        }

        getHandle().spawnEntity(entity, Cause.of(NamedCause.source(Pore.getPlugin())));
        return PoreEntity.of(entity);
    }

    @SuppressWarnings("deprecation")
    @Override
    public LivingEntity spawnCreature(Location loc, EntityType type) {
        Entity spawned = spawnEntity(loc, type);
        if (!(spawned instanceof LivingEntity)) {
            throw new IllegalArgumentException("Call to spawnCreature non-living entity type");
        }
        return (LivingEntity) spawned;
    }

    @Override
    @SuppressWarnings("deprecation")
    public LivingEntity spawnCreature(Location loc, CreatureType type) {
        return spawnCreature(loc, type.toEntityType());
    }

    @Override
    public LightningStrike strikeLightning(Location loc) {
        return (LightningStrike) spawnEntity(loc, EntityType.LIGHTNING);
    }

    @Override
    public LightningStrike strikeLightningEffect(Location loc) {
        // Create a lightning entity and mark it as an effect only. Sponge
        // supports a boolean flag for this; if implementations do not, the
        // lightning will behave like a normal strike.
        Optional<org.spongepowered.api.entity.Entity> created =
                getHandle().createEntity(EntityTypes.LIGHTNING, VectorConverter.create3d(loc));
        if (!created.isPresent()) {
            return null;
        }
        org.spongepowered.api.entity.weather.Lightning lightning =
                (org.spongepowered.api.entity.weather.Lightning) created.get();
        try {
            lightning.setEffect(true);
        } catch (Exception ignored) {
            // Older API versions may not support this method
        }
        getHandle().spawnEntity(lightning, Cause.of(NamedCause.source(Pore.getPlugin())));
        return PoreLightningStrike.of(lightning);
    }

    @Override
    public List<Entity> getEntities() {
        return PoreCollections.<org.spongepowered.api.entity.Entity, Entity>transformToList(
                getHandle().getEntities(), WrapperConverter
                        .<org.spongepowered.api.entity.Entity, PoreEntity>getConverter()
        );
    }

    @Override
    public List<LivingEntity> getLivingEntities() {
        // This is basically copying every time, unfortunately there is no real better way because we can't
        // filter lists using Guava
        List<LivingEntity> living = Lists.newArrayList();
        living.addAll(getHandle().getEntities().stream().filter(e -> e instanceof Living)
                .map(e -> PoreLivingEntity.of((Living) e)).collect(Collectors.toList()));
        return living;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> Collection<T> getEntitiesByClass(Class<T> cls) {
        return (Collection<T>) Collections2.filter(getEntities(), Predicates.instanceOf(cls));
    }

    @Override
    @SuppressWarnings("unchecked")
    @Deprecated
    public <T extends Entity> Collection<T> getEntitiesByClass(final Class<T>... classes) {
        return (Collection<T>) getEntitiesByClasses(classes);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<Entity> getEntitiesByClasses(final Class<?>... classes) {
        return Collections2.filter(getEntities(), entity -> {
            for (Class<?> clazz : classes) {
                if (clazz.isInstance(entity)) {
                    return true;
                }
            }

            return false;
        });
    }

    @Override
    public List<Player> getPlayers() {
        //TODO: possibly optimize this (there is no real way other than Sponge implementing something to help
        // with that)
        // see getLivingEntities() for explanation
        List<Player> players = Lists.newArrayList();
        players.addAll(getHandle().getEntities().stream()
                .filter(e -> e instanceof org.spongepowered.api.entity.living.player.Player)
                .map(e -> PorePlayer.of((org.spongepowered.api.entity.living.player.Player) e))
                .collect(Collectors.toList()));
        return players;
    }

    @Override
    public Collection<Entity> getNearbyEntities(Location location, double x, double y, double z) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public String getName() {
        return getHandle().getName();
    }

    @Override
    public UUID getUID() {
        return getHandle().getUniqueId();
    }

    @Override
    public Location getSpawnLocation() {
        return LocationConverter.of(getHandle().getSpawnLocation());
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z) {
        Vector3i position = new Vector3i(x, y, z);
        getHandle().getProperties().setSpawnPosition(position);
        return getHandle().getProperties().getSpawnPosition().equals(position);
    }

    @Override
    public long getTime() {
        return getHandle().getProperties().getWorldTime() % TICKS_PER_DAY;
    }

    @Override
    public void setTime(long time) {
        long catchup = TICKS_PER_DAY - getHandle().getProperties().getWorldTime() % TICKS_PER_DAY;
        getHandle().getProperties().setWorldTime(getHandle().getProperties().getWorldTime() + catchup + time);
    }

    @Override
    public long getFullTime() {
        return getHandle().getProperties().getWorldTime();
    }

    @Override
    public void setFullTime(long time) {
        getHandle().getProperties().setWorldTime(0L);
    }

    @Override
    public boolean hasStorm() {
        return getHandle().getWeather().equals(Weathers.RAIN)
                || getHandle().getWeather().equals(Weathers.THUNDER_STORM);
    }

    @Override
    public void setStorm(boolean hasStorm) {
        getHandle().setWeather(hasStorm ? Weathers.RAIN : Weathers.CLEAR);
    }

    @Override
    public int getWeatherDuration() {
        return (int) getHandle().getRemainingDuration();
    }

    @Override
    public void setWeatherDuration(int duration) {
        getHandle().setWeather(getHandle().getWeather(), duration);
    }

    @Override
    public boolean isThundering() {
        return getHandle().getProperties().isThundering();
    }

    @Override
    public void setThundering(boolean thundering) {
        getHandle().getProperties().setThundering(thundering);
    }

    @Override
    public int getThunderDuration() {
        return isThundering() ? getHandle().getProperties().getThunderTime() : 0;
    }

    @Override
    public void setThunderDuration(int duration) {
        getHandle().getProperties().setThunderTime(duration);
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power, boolean setFire) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power, boolean setFire,
            boolean breakBlocks) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public boolean createExplosion(Location loc, float power) {
        Explosion explosion = Explosion.builder()
                .world(((PoreWorld) loc.getWorld()).getHandle())
                .origin(LocationConverter.toVector3d(loc))
                .radius(power)
                .build();

        explosion.getWorld().triggerExplosion(explosion);
        return true; // TODO
    }

    @Override
    public boolean createExplosion(Location loc, float power, boolean setFire) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public Environment getEnvironment() {
        return EnvironmentConverter.of(getHandle().getDimension().getType());
    }

    @Override
    public long getSeed() {
        return getHandle().getCreationSettings().getSeed();
    }

    @Override
    public boolean getPVP() {
        // Access Sponge world's properties which expose PVP status
        return getHandle().getProperties().isPVPEnabled();
    }

    @Override
    public void setPVP(boolean pvp) {
        getHandle().getProperties().setPVPEnabled(pvp);
    }

    @Override
    public ChunkGenerator getGenerator() {
        // Sponge only exposes the world generator through its own API. There is
        // no direct Bukkit equivalent, so return null which matches Bukkit's
        // behaviour for vanilla worlds.
        return null;
    }

    @Override
    public void save() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public List<BlockPopulator> getPopulators() {
        throw new NotImplementedException("TODO");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Entity> T spawn(Location location, Class<T> clazz) throws IllegalArgumentException {
        Entity spawned = spawnEntity(location, EntityType.fromClass(clazz));
        if (clazz.isAssignableFrom(spawned.getClass())) {
            return (T) spawned;

        } else {
            throw new IllegalStateException("Spawned entity was not of the appropriate type: "
                    + "Expected " + clazz + ", found " + spawned.getClass());
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public FallingBlock spawnFallingBlock(Location location, Material material, byte data)
            throws IllegalArgumentException {
        Entity spawned = spawnEntity(location, EntityType.FALLING_BLOCK);
        if (!(spawned instanceof org.spongepowered.api.entity.FallingBlock)) {
            throw new IllegalStateException("Spawned entity was not falling block!"); //TODO: exception type?
        }
        org.spongepowered.api.entity.FallingBlock fb = (org.spongepowered.api.entity.FallingBlock) spawned;
        //TODO: set type and such
        return PoreFallingSand.of(fb);
    }

    @SuppressWarnings("deprecation")
    @Override
    public FallingBlock spawnFallingBlock(Location location, int blockId, byte blockData)
            throws IllegalArgumentException {
        return spawnFallingBlock(location, Material.getMaterial(blockId), blockData);
    }

    @Override
    public void playEffect(Location location, Effect effect, int data) {
        this.playEffect(location, effect, data, DEFAULT_EFFECT_RADIUS);
    }

    @Override
    public void playEffect(Location location, Effect effect, int data, int radius) {
        if (effect.getType() == Effect.Type.SOUND) {
            //noinspection ConstantConditions
            getHandle().playSound(EffectConverter.toSound(effect, data), VectorConverter.create3d(location), radius);
        } else {
            //noinspection ConstantConditions
            //TODO: define a quantity
            getHandle().spawnParticles(
                    ParticleEffect.builder().type(EffectConverter.toParticle(effect)).build(),
                    VectorConverter.create3d(location),
                    radius);
        }
    }

    @Override
    public <T> void playEffect(Location location, Effect effect, T data) {
        this.playEffect(location, effect, data, DEFAULT_EFFECT_RADIUS);
    }

    @Override
    public <T> void playEffect(Location location, Effect effect, T data, int radius) {
        if ((data != null && data.getClass().equals(effect.getData()))
                || (data == null && effect.getData() == null)) {
            this.playEffect(location, effect, data == null ? 0 : EffectConverter.getDataValue(effect, data), radius);
        } else {
            throw new IllegalArgumentException("Invalid data type for effect!");
        }
    }

    @Override
    public ChunkSnapshot getEmptyChunkSnapshot(int x, int z, boolean includeBiome,
            boolean includeBiomeTempRain) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void setSpawnFlags(boolean allowMonsters, boolean allowAnimals) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public boolean getAllowAnimals() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public boolean getAllowMonsters() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public Biome getBiome(int x, int z) {
        return BiomeConverter.of(getHandle().getBiome(x, z));
    }

    @Override
    public void setBiome(int x, int z, Biome bio) {
        getHandle().setBiome(x, z, BiomeConverter.of(bio));
    }

    @Override
    public double getTemperature(int x, int z) {
        return getHandle().getBiome(x, z).getTemperature();
    }

    @Override
    public double getHumidity(int x, int z) {
        return getHandle().getBiome(x, z).getHumidity();
    }

    @Override
    public int getMaxHeight() {
        return getHandle().getDimension().getBuildHeight();
    }

    @Override
    public int getSeaLevel() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public boolean getKeepSpawnInMemory() {
        return getHandle().getWorldStorage().getWorldProperties().doesKeepSpawnLoaded();
    }

    @Override
    public void setKeepSpawnInMemory(boolean keepLoaded) {
        getHandle().getProperties().setKeepSpawnLoaded(keepLoaded);
    }

    @Override
    public boolean isAutoSave() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void setAutoSave(boolean value) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public Difficulty getDifficulty() {
        return DifficultyConverter.of(getHandle().getProperties().getDifficulty());
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        getHandle().getProperties().setDifficulty(DifficultyConverter.of(difficulty));
    }

    @Override
    public File getWorldFolder() {
        return new File(Bukkit.getWorldContainer(), getHandle().getName()); //TODO: not sure this will always work
    }

    @Override
    public WorldType getWorldType() {
        return GeneratorTypeConverter.of(getHandle().getCreationSettings().getGeneratorType());
    }

    @Override
    public boolean canGenerateStructures() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public long getTicksPerAnimalSpawns() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void setTicksPerAnimalSpawns(int ticksPerAnimalSpawns) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public long getTicksPerMonsterSpawns() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void setTicksPerMonsterSpawns(int ticksPerMonsterSpawns) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public int getMonsterSpawnLimit() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void setMonsterSpawnLimit(int limit) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public int getAnimalSpawnLimit() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void setAnimalSpawnLimit(int limit) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public int getWaterAnimalSpawnLimit() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void setWaterAnimalSpawnLimit(int limit) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public int getAmbientSpawnLimit() {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void setAmbientSpawnLimit(int limit) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void playSound(Location location, Sound sound, float volume, float pitch) {
        getHandle().playSound(SoundConverter.of(sound), VectorConverter.create3d(location), (double) volume,
                (double) pitch);
    }

    @Override
    public String[] getGameRules() {
        Set<String> rules = getHandle().getGameRules().keySet();
        return rules.toArray(new String[rules.size()]);
    }

    @Override
    public String getGameRuleValue(String rule) {
        return getHandle().getGameRule(rule).orElse(null);
    }

    @Override
    public boolean setGameRuleValue(String rule, String value) {
        if (rule == null) {
            return false;
        }
        getHandle().getProperties().setGameRule(rule, value);
        return true; // doesn't seem to be capable of failing in Sponge
    }

    @Override
    public boolean isGameRule(String rule) {
        return getHandle().getGameRule(rule).isPresent();
    }

    @Override
    public WorldBorder getWorldBorder() {
        return PoreWorldBorder.of(getHandle().getWorldBorder(), this);
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public void sendPluginMessage(Plugin source, String channel, byte[] message) {
        throw new NotImplementedException("TODO");
    }

    @Override
    public Set<String> getListeningPluginChannels() {
        throw new NotImplementedException("TODO");
    }
}
