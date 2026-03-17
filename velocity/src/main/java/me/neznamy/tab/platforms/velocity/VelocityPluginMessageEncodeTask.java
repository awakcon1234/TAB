package me.neznamy.tab.platforms.velocity;

import com.velocitypowered.api.proxy.ServerConnection;
import lombok.RequiredArgsConstructor;
import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.TabConstants.CpuUsageCategory;
import me.neznamy.tab.shared.proxy.message.outgoing.OutgoingMessage;

/**
 * Task for encoding and sending plugin message to a specific Velocity backend connection.
 */
@RequiredArgsConstructor
public class VelocityPluginMessageEncodeTask implements Runnable {

    /** Player to send plugin message to */
    private final VelocityTabPlayer player;

    /** Backend connection the message was created for */
    private final ServerConnection connection;

    /** Plugin message to encode and send */
    private final OutgoingMessage message;

    @Override
    public void run() {
        long time = System.nanoTime();
        byte[] msg = message.write().toByteArray();
        TAB.getInstance().getCpu().addTime("Plugin message handling", CpuUsageCategory.PLUGIN_MESSAGE_ENCODE, System.nanoTime() - time);
        time = System.nanoTime();
        player.sendPluginMessage(connection, msg);
        TAB.getInstance().getCpu().addTime("Plugin message handling", CpuUsageCategory.PLUGIN_MESSAGE_SEND, System.nanoTime() - time);
    }
}