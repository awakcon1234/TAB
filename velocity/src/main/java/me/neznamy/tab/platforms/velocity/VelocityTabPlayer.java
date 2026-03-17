package me.neznamy.tab.platforms.velocity;

import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.Player;
import me.neznamy.tab.shared.cpu.CpuManager;
import me.neznamy.tab.shared.chat.component.TabComponent;
import me.neznamy.tab.shared.proxy.ProxyTabPlayer;
import me.neznamy.tab.shared.proxy.message.outgoing.OutgoingMessage;
import org.jetbrains.annotations.NotNull;

/**
 * TabPlayer implementation for Velocity.
 */
public class VelocityTabPlayer extends ProxyTabPlayer {

    /**
     * Constructs new instance for given player
     *
     * @param   platform
     *          Server platform
     * @param   p
     *          velocity player
     */
    public VelocityTabPlayer(@NotNull VelocityPlatform platform, @NotNull Player p) {
        super(platform, p, p.getUniqueId(), p.getUsername(), p.getCurrentServer().map(s ->
                s.getServerInfo().getName()).orElse("null"), p.getProtocolVersion().getProtocol());
    }
    
    @Override
    public boolean hasPermission0(@NotNull String permission) {
        return getPlayer().hasPermission(permission);
    }
    
    @Override
    public int getPing() {
        return (int) getPlayer().getPing();
    }

    @Override
    public void sendMessage(@NotNull TabComponent message) {
        getPlayer().sendMessage(message.toAdventure());
    }

    @Override
    @NotNull
    public Player getPlayer() {
        return (Player) player;
    }

    @Override
    public VelocityPlatform getPlatform() {
        return (VelocityPlatform) platform;
    }

    @Override
    public void sendPluginMessage(@NotNull OutgoingMessage message) {
        getPlayer().getCurrentServer().ifPresent(currentServer ->
                CpuManager.getPluginMessageEncodeThread().execute(new VelocityPluginMessageEncodeTask(this, currentServer, message))
        );
    }

    @Override
    public void sendPluginMessage(byte[] message) {
        getPlayer().getCurrentServer().ifPresent(currentServer -> sendPluginMessage(currentServer, message));
    }

    public void sendPluginMessage(@NotNull ServerConnection connection, byte[] message) {
        try {
            if (getPlayer().getCurrentServer().orElse(null) != connection) return;
            connection.sendPluginMessage(getPlatform().getMCI(), message);
        } catch (IllegalStateException VelocityBeingVelocityException) {
            // java.lang.IllegalStateException: Not connected to server!
        }
    }
}