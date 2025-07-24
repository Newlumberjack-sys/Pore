/*
 * Pore
 * Copyright (c) 2014-2016, Lapis <https://github.com/LapisBlue>
 *
 * The MIT License
 */
package blue.lapis.pore.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import blue.lapis.pore.PoreTests;
import blue.lapis.pore.converter.type.world.GeneratorTypeConverter;

import com.google.common.collect.ImmutableList;
import org.bukkit.OfflinePlayer;
import org.junit.Before;
import org.junit.Test;
import org.spongepowered.api.Game;
import org.spongepowered.api.Platform;
import org.spongepowered.api.Server;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.service.ServiceManager;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.service.whitelist.WhitelistService;
import org.spongepowered.api.world.DimensionTypes;
import org.spongepowered.api.world.GeneratorTypes;
import org.spongepowered.api.world.storage.WorldProperties;
import org.spongepowered.api.entity.living.player.User;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class PoreServerTest {

    private PoreServer server;
    private Game game;
    private Server spongeServer;
    private ServiceManager services;
    private WhitelistService whitelist;
    private UserStorageService users;
    private WorldProperties defaultProps;

    @Before
    public void setUp() {
        PoreTests.mockPlugin();

        game = mock(Game.class);
        spongeServer = mock(Server.class);
        when(game.getServer()).thenReturn(spongeServer);

        Platform platform = mock(Platform.class);
        PluginContainer impl = mock(PluginContainer.class);
        when(impl.getName()).thenReturn("ImplName");
        when(impl.getId()).thenReturn("impl-id");
        when(platform.getImplementation()).thenReturn(impl);
        when(game.getPlatform()).thenReturn(platform);

        defaultProps = mock(WorldProperties.class);
        when(spongeServer.getDefaultWorld()).thenReturn(Optional.of(defaultProps));

        WorldProperties endProps = mock(WorldProperties.class);
        when(endProps.getDimensionType()).thenReturn(DimensionTypes.THE_END);
        WorldProperties netherProps = mock(WorldProperties.class);
        when(netherProps.getDimensionType()).thenReturn(DimensionTypes.NETHER);
        when(spongeServer.getAllWorldProperties()).thenReturn(ImmutableList.of(defaultProps, endProps, netherProps));

        services = mock(ServiceManager.class);
        whitelist = mock(WhitelistService.class);
        users = mock(UserStorageService.class);
        when(game.getServiceManager()).thenReturn(services);
        when(services.provide(WhitelistService.class)).thenReturn(Optional.of(whitelist));
        when(services.provideUnchecked(UserStorageService.class)).thenReturn(users);

        server = new PoreServer(game, org.slf4j.LoggerFactory.getLogger("test"));
    }

    @Test
    public void testNameAndId() {
        assertEquals("ImplName", server.getServerName());
        assertEquals("impl-id", server.getServerId());
    }

    @Test
    public void testWorldProperties() {
        when(defaultProps.getGeneratorType()).thenReturn(GeneratorTypes.FLAT);
        when(defaultProps.usesMapFeatures()).thenReturn(true);

        assertEquals(GeneratorTypeConverter.of(GeneratorTypes.FLAT).getName(), server.getWorldType());
        assertTrue(server.getGenerateStructures());
        assertTrue(server.getAllowEnd());
        assertTrue(server.getAllowNether());
    }

    @Test
    public void testWhitelist() {
        GameProfile profile = GameProfile.of(UUID.randomUUID(), "tester");
        User user = mock(User.class);
        when(users.get(profile)).thenReturn(Optional.of(user));
        when(whitelist.getWhitelistedProfiles()).thenReturn(ImmutableList.of(profile));

        when(spongeServer.hasWhitelist()).thenReturn(false);
        assertTrue(!server.hasWhitelist());
        server.setWhitelist(true);
        verify(spongeServer).setHasWhitelist(true);

        Set<OfflinePlayer> players = server.getWhitelistedPlayers();
        assertEquals(1, players.size());
    }
}
