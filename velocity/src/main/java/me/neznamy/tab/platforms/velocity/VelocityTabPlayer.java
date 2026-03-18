package me.neznamy.tab.platforms.velocity;

import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.proxy.connection.backend.VelocityServerConnection;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.connection.MinecraftConnection;
import me.neznamy.tab.shared.cpu.CpuManager;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.chat.component.TabComponent;
import me.neznamy.tab.shared.proxy.ProxyTabPlayer;
import me.neznamy.tab.shared.proxy.message.outgoing.PlayerJoin;
import me.neznamy.tab.shared.proxy.message.outgoing.OutgoingMessage;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * TabPlayer implementation for Velocity.
 */
public class VelocityTabPlayer extends ProxyTabPlayer {

    /** Maximum number of retries for join bridge message while backend is switching */
    private static final int JOIN_PLUGIN_MESSAGE_MAX_RETRIES = 40;

    /** Delay between join bridge message retries in milliseconds */
    private static final int JOIN_PLUGIN_MESSAGE_RETRY_DELAY = 50;

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
        getPlayer().getCurrentServer().ifPresent(currentServer -> {
            if (currentServer instanceof VelocityServerConnection velocityConnection && !isBackendReady(velocityConnection)) {
                if (message instanceof PlayerJoin) {
                    retryJoinPluginMessage(velocityConnection, message, 0);
                } else {
                    TAB.getInstance().debug("Dropping bridge message " + message.getClass().getSimpleName() + " for " + getName() +
                            " because backend " + velocityConnection.getServerInfo().getName() + " is not ready yet " + describeBackendState(velocityConnection));
                }
                return;
            }
            CpuManager.getPluginMessageEncodeThread().execute(new VelocityPluginMessageEncodeTask(this, currentServer, message));
        });
    }

    @Override
    public void sendPluginMessage(byte[] message) {
        getPlayer().getCurrentServer().ifPresent(currentServer -> sendPluginMessage(currentServer, message));
    }

    public void sendPluginMessage(@NotNull ServerConnection connection, byte[] message) {
        if (connection instanceof VelocityServerConnection velocityConnection) {
            if (!isBackendReady(velocityConnection)) {
                TAB.getInstance().debug("Dropping encoded bridge payload (" + message.length + " bytes) for " + getName() +
                        " because backend " + velocityConnection.getServerInfo().getName() + " is not ready yet " + describeBackendState(velocityConnection));
                return;
            }
            MinecraftConnection minecraftConnection = velocityConnection.getConnection();
            if (minecraftConnection != null) {
                minecraftConnection.eventLoop().execute(() -> sendPluginMessage0(connection, message));
                return;
            }
        }
        sendPluginMessage0(connection, message);
    }

    private void sendPluginMessage0(@NotNull ServerConnection connection, byte[] message) {
        try {
            if (getPlayer().getCurrentServer().orElse(null) != connection) return;
            if (connection instanceof VelocityServerConnection velocityConnection && !isBackendReady(velocityConnection)) return;
            VelocityBridgeMessageLogger.logOutgoing(this, connection.getServerInfo().getName(), message);
            connection.sendPluginMessage(getPlatform().getMCI(), message);
        } catch (IllegalStateException VelocityBeingVelocityException) {
            // java.lang.IllegalStateException: Not connected to server!
        }
    }

    private void retryJoinPluginMessage(@NotNull VelocityServerConnection connection, @NotNull OutgoingMessage message, int attempt) {
        if (getPlayer().getCurrentServer().orElse(null) != connection) {
            TAB.getInstance().debug("Dropping delayed PlayerJoin bridge message for " + getName() + " because player switched backend before retry completed");
            return;
        }
        if (isBackendReady(connection)) {
            TAB.getInstance().debug("Sending delayed PlayerJoin bridge message for " + getName() + " after backend became ready " + describeBackendState(connection));
            CpuManager.getPluginMessageEncodeThread().execute(new VelocityPluginMessageEncodeTask(this, connection, message));
            return;
        }
        if (attempt >= JOIN_PLUGIN_MESSAGE_MAX_RETRIES) {
            TAB.getInstance().debug("Dropping PlayerJoin bridge message for " + getName() + " after " + JOIN_PLUGIN_MESSAGE_MAX_RETRIES +
                    " retries because backend did not become ready " + describeBackendState(connection));
            return;
        }
        ((ConnectedPlayer) getPlayer()).getConnection().eventLoop().schedule(
                () -> retryJoinPluginMessage(connection, message, attempt + 1),
                JOIN_PLUGIN_MESSAGE_RETRY_DELAY,
                TimeUnit.MILLISECONDS
        );
    }

    private boolean isBackendReady(@NotNull VelocityServerConnection connection) {
        return connection.isActive() && connection.hasCompletedJoin() && connection.getPhase().consideredComplete();
    }

    @NotNull
    private String describeBackendState(@NotNull VelocityServerConnection connection) {
        return "[active=" + connection.isActive() + ", completedJoin=" + connection.hasCompletedJoin() +
                ", phase=" + connection.getPhase().getClass().getSimpleName() + ", phaseComplete=" + connection.getPhase().consideredComplete() + "]";
    }
}