package blue.lapis.pore.impl;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import blue.lapis.pore.PoreTests;

import com.flowpowered.math.vector.Vector3i;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.Before;
import org.junit.Test;
import org.spongepowered.api.data.TransactionResult; // placeholder maybe
import org.spongepowered.api.data.DataTransactionResult;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.weather.Lightning;
import org.spongepowered.api.world.Chunk;

import java.util.Optional;

public class WorldChunkOpsTest {

    private PoreWorld world;
    private org.spongepowered.api.world.World sponge;

    @Before
    public void setup() {
        PoreTests.mockPlugin();
        sponge = mock(org.spongepowered.api.world.World.class);
        world = PoreWorld.of(sponge);
    }

    @Test
    public void testLoadChunk() {
        Chunk spongeChunk = mock(Chunk.class);
        when(sponge.loadChunk(any(Vector3i.class), eq(true))).thenReturn(Optional.of(spongeChunk));

        assertTrue(world.loadChunk(1, 2, true));
        verify(sponge).loadChunk(new Vector3i(1, 0, 2), true);
    }

    @Test
    public void testUnloadChunk() {
        Chunk spongeChunk = mock(Chunk.class);
        when(sponge.getChunk(new Vector3i(1, 0, 2))).thenReturn(Optional.of(spongeChunk));
        when(spongeChunk.unloadChunk()).thenReturn(true);

        assertTrue(world.unloadChunk(1, 2, true, true));
        verify(spongeChunk).unloadChunk();
    }

    @Test
    public void testDropItem() {
        Entity spongeEntity = mock(Entity.class, withSettings().extraInterfaces(org.spongepowered.api.entity.Item.class));
        when(sponge.createEntity(eq(EntityTypes.ITEM), any())).thenReturn(Optional.of(spongeEntity));
        when(sponge.spawnEntity(eq(spongeEntity), any())).thenReturn(true);
        when(((org.spongepowered.api.entity.Item) spongeEntity).offer(eq(Keys.REPRESENTED_ITEM), any())).thenReturn(DataTransactionResult.successNoData());

        Location loc = new Location(mock(World.class), 0, 0, 0);
        ItemStack stack = new ItemStack(Material.STONE);
        Item item = world.dropItem(loc, stack);

        assertNotNull(item);
        verify(sponge).spawnEntity(eq(spongeEntity), any());
        verify((org.spongepowered.api.entity.Item) spongeEntity).offer(eq(Keys.REPRESENTED_ITEM), any());
    }

    @Test
    public void testStrikeLightningEffect() {
        Lightning lightning = mock(Lightning.class);
        when(sponge.createEntity(eq(EntityTypes.LIGHTNING), any())).thenReturn(Optional.of((Entity) lightning));
        when(sponge.spawnEntity(eq((Entity) lightning), any())).thenReturn(true);

        Location loc = new Location(mock(World.class), 1, 2, 3);
        world.strikeLightningEffect(loc);

        verify(lightning).setEffect(true);
        verify(sponge).spawnEntity(eq((Entity) lightning), any());
    }
}
