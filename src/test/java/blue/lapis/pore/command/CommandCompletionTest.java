package blue.lapis.pore.command;

import blue.lapis.pore.Pore;
import blue.lapis.pore.PoreTests;
import blue.lapis.pore.impl.command.PoreCommandMap;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.spongepowered.api.Game;
import org.spongepowered.api.command.CommandCallable;
import org.spongepowered.api.command.CommandManager;
import org.spongepowered.api.command.CommandMapping;
import org.spongepowered.api.command.CommandSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CommandCompletionTest {

    private CommandManager manager;
    private Command command;

    @Before
    public void setUp() {
        PoreTests.mockPlugin();
        Game game = Pore.getGame();
        manager = mock(CommandManager.class);
        when(game.getCommandManager()).thenReturn(manager);

        command = mock(Command.class);
        when(command.getName()).thenReturn("test");
        when(command.getAliases()).thenReturn(Collections.emptyList());
        when(command.tabComplete(any(), eq("test"), any(String[].class)))
                .thenReturn(Arrays.asList("one", "two"));
    }

    @Test
    public void commandRegistersAndCompletes() {
        Server server = mock(Server.class);
        PoreCommandMap map = new PoreCommandMap(server);

        ArgumentCaptor<CommandCallable> captor = ArgumentCaptor.forClass(CommandCallable.class);
        when(manager.register(any(), captor.capture(), anyList())).thenReturn(Optional.of(mock(CommandMapping.class)));

        map.register("test", "pore", command);

        CommandCallable callable = captor.getValue();
        CommandSource source = mock(CommandSource.class);
        List<String> suggestions = callable.getSuggestions(source, "");
        assertEquals(Arrays.asList("one", "two"), suggestions);
    }
}
