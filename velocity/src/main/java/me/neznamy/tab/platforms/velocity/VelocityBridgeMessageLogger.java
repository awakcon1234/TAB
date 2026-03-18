package me.neznamy.tab.platforms.velocity;

import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.chat.component.TabTextComponent;
import me.neznamy.tab.shared.platform.TabPlayer;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Temporary helper for dumping all outgoing TAB bridge payloads on Velocity.
 */
public final class VelocityBridgeMessageLogger {

    private VelocityBridgeMessageLogger() {
    }

    public static void logOutgoing(@NotNull TabPlayer player, @NotNull String server, byte[] bytes) {
        log("[BridgeDump] OUT player=" + player.getName() + " server=" + server + " bytes=" + bytes.length);
        log("[BridgeDump] " + formatPayload(bytes));
    }

    @NotNull
    private static String formatPayload(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String type = in.readUTF();
            return switch (type) {
                case "PlayerJoin" -> formatPlayerJoin(in);
                case "Permission" -> "type=Permission permission=" + quote(in.readUTF());
                case "Expansion" -> "type=Expansion placeholder=" + quote(in.readUTF()) + " value=" + quote(in.readUTF());
                case "Placeholder" -> "type=Placeholder identifier=" + quote(in.readUTF()) + " refresh=" + in.readInt();
                case "PacketPlayOutScoreboardObjective" -> formatObjective(in);
                case "PacketPlayOutScoreboardDisplayObjective" -> "type=PacketPlayOutScoreboardDisplayObjective slot=" + in.readInt() + " objective=" + quote(in.readUTF());
                case "PacketPlayOutScoreboardScore" -> formatScore(in);
                case "PacketPlayOutScoreboardTeam" -> formatTeam(in);
                case "Unload" -> "type=Unload";
                default -> "type=" + quote(type) + " rawUtf8=" + quote(toPrintable(bytes));
            };
        } catch (Throwable t) {
            return "decode-failed error=" + quote(String.valueOf(t)) + " rawUtf8=" + quote(toPrintable(bytes));
        }
    }

    @NotNull
    private static String formatPlayerJoin(@NotNull DataInputStream in) throws IOException {
        int protocolVersion = in.readInt();
        boolean forwardGroup = in.readBoolean();
        int placeholderCount = in.readInt();
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < placeholderCount; i++) {
            placeholders.add(in.readUTF() + "@" + in.readInt());
        }
        int replacementCount = in.readInt();
        List<String> replacements = new ArrayList<>();
        for (int i = 0; i < replacementCount; i++) {
            String identifier = in.readUTF();
            int rules = in.readInt();
            List<String> values = new ArrayList<>();
            for (int j = 0; j < rules; j++) {
                values.add(quote(in.readUTF()) + "->" + quote(in.readUTF()));
            }
            replacements.add(identifier + "=" + values);
        }
        boolean unlimitedNametags = in.readBoolean();
        return "type=PlayerJoin protocolVersion=" + protocolVersion +
                " forwardGroup=" + forwardGroup +
                " placeholders=" + placeholders +
                " replacements=" + replacements +
                " unlimitedNametags=" + unlimitedNametags;
    }

    @NotNull
    private static String formatObjective(@NotNull DataInputStream in) throws IOException {
        String objective = in.readUTF();
        int action = in.readInt();
        StringBuilder builder = new StringBuilder("type=PacketPlayOutScoreboardObjective objective=")
                .append(quote(objective))
                .append(" action=")
                .append(action);
        if (action == 0 || action == 2) {
            builder.append(" title=").append(quote(in.readUTF()));
            builder.append(" display=").append(in.readInt());
            boolean hasNumberFormat = in.readBoolean();
            builder.append(" hasNumberFormat=").append(hasNumberFormat);
            if (hasNumberFormat) builder.append(" numberFormat=").append(quote(in.readUTF()));
        }
        return builder.toString();
    }

    @NotNull
    private static String formatScore(@NotNull DataInputStream in) throws IOException {
        String objective = in.readUTF();
        int action = in.readInt();
        String scoreHolder = in.readUTF();
        StringBuilder builder = new StringBuilder("type=PacketPlayOutScoreboardScore objective=")
                .append(quote(objective))
                .append(" action=")
                .append(action)
                .append(" scoreHolder=")
                .append(quote(scoreHolder));
        if (action == 0) {
            builder.append(" score=").append(in.readInt());
            boolean hasDisplayName = in.readBoolean();
            builder.append(" hasDisplayName=").append(hasDisplayName);
            if (hasDisplayName) builder.append(" displayName=").append(quote(in.readUTF()));
            boolean hasNumberFormat = in.readBoolean();
            builder.append(" hasNumberFormat=").append(hasNumberFormat);
            if (hasNumberFormat) builder.append(" numberFormat=").append(quote(in.readUTF()));
        }
        return builder.toString();
    }

    @NotNull
    private static String formatTeam(@NotNull DataInputStream in) throws IOException {
        String name = in.readUTF();
        int action = in.readInt();
        StringBuilder builder = new StringBuilder("type=PacketPlayOutScoreboardTeam name=")
                .append(quote(name))
                .append(" action=")
                .append(action);
        if (action == 0 || action == 2) {
            builder.append(" prefix=").append(quote(in.readUTF()));
            builder.append(" suffix=").append(quote(in.readUTF()));
            builder.append(" options=").append(in.readInt());
            builder.append(" visibility=").append(quote(in.readUTF()));
            builder.append(" collision=").append(quote(in.readUTF()));
            builder.append(" color=").append(in.readInt());
        }
        if (action == 0) {
            int playerCount = in.readInt();
            List<String> players = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                players.add(in.readUTF());
            }
            builder.append(" players=").append(players);
        }
        return builder.toString();
    }

    @NotNull
    private static String quote(@NotNull String text) {
        return '"' + text
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                .replace("\"", "\\\"") + '"';
    }

    @NotNull
    private static String toPrintable(byte[] bytes) {
        String raw = new String(bytes, StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(raw.length());
        for (char character : raw.toCharArray()) {
            if (character == '\n' || character == '\r' || character == '\t' || !Character.isISOControl(character)) {
                out.append(character);
            } else {
                out.append('?');
            }
        }
        return out.toString();
    }

    private static void log(@NotNull String message) {
        TAB.getInstance().getPlatform().logInfo(new TabTextComponent(message));
    }
}