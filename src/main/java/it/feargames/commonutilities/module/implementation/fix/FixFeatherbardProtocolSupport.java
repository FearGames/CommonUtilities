package it.feargames.commonutilities.module.implementation.fix;

import com.comphenix.packetwrapper.WrapperPlayServerScoreboardTeam;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.google.gson.JsonParser;
import it.feargames.commonutilities.annotation.ConfigValue;
import it.feargames.commonutilities.annotation.RegisterListeners;
import it.feargames.commonutilities.module.Module;
import it.feargames.commonutilities.service.PluginService;
import it.feargames.commonutilities.service.ProtocolServiceWrapper;
import it.feargames.commonutilities.util.ReflectionUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.event.Listener;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
@RegisterListeners
public class FixFeatherbardProtocolSupport implements Module, Listener {

    private final static String LISTENER_ID = "FixFeatherboardProtocolSupport";

    private ProtocolServiceWrapper wrapper;

    @ConfigValue
    private Boolean enabled = true;

    @Override
    public void onLoad(String name, PluginService service, ProtocolServiceWrapper wrapper) {
        this.wrapper = wrapper;
    }

    private static String getFormatString(final BaseComponent component) {
        StringBuilder formatBuilder = new StringBuilder();
        formatBuilder.append(component.getColor());
        if (component.isBold()) formatBuilder.append(ChatColor.BOLD);
        if (component.isItalic()) formatBuilder.append(ChatColor.ITALIC);
        if (component.isUnderlined()) formatBuilder.append(ChatColor.UNDERLINE);
        if (component.isStrikethrough()) formatBuilder.append(ChatColor.STRIKETHROUGH);
        if (component.isObfuscated()) formatBuilder.append(ChatColor.MAGIC);
        return formatBuilder.toString();
    }

    @Override
    public void onEnable() {
        wrapper.getProtocolService().ifPresent(protocol -> {
            // Protocol docs: http://wiki.vg/Protocol#Teams
            protocol.addSendingListener(LISTENER_ID, ListenerPriority.HIGHEST, PacketType.Play.Server.SCOREBOARD_TEAM, event -> {
                if(!ReflectionUtils.getStackTraceElement(24).getClassName().equals("be.maximvdw.featherboard.dm")) {
                    return; // Catch only FeatherBoard packets.
                }

                final WrapperPlayServerScoreboardTeam wrapper = new WrapperPlayServerScoreboardTeam(event.getPacket());
                final String rawMessage = new JsonParser().parse(wrapper.getPrefix().getJson()).getAsJsonObject().get("text").getAsString();
                final BaseComponent[] messageComponents = TextComponent.fromLegacyText(rawMessage);

                final StringBuilder prefixBuilder = new StringBuilder(16);
                boolean useSuffix = false;
                final StringBuilder suffixBuilder = new StringBuilder(16);

                for (BaseComponent component : messageComponents) {
                    final String componentText = ChatColor.stripColor(component.toLegacyText());
                    final String componentFormat = getFormatString(component);
                    String remaining = componentText;
                    while (!remaining.isEmpty()) {
                        if (!useSuffix) {
                            final int availablePrefix = prefixBuilder.capacity() - prefixBuilder.length() - componentFormat.length();
                            if (availablePrefix < 1) {
                                // Ok, let's use suffix from now on!
                                useSuffix = true;
                                continue;
                            }
                            final int handled = Math.min(availablePrefix, remaining.length());
                            prefixBuilder.append(componentFormat).append(remaining, 0, handled);
                            remaining = remaining.substring(handled);
                        } else {
                            // Ok, time to overflow!
                            suffixBuilder.append(componentFormat).append(remaining);
                            break;
                        }
                    }
                }
                wrapper.setPrefix(WrappedChatComponent.fromText(prefixBuilder.toString()));
                wrapper.setSuffix(WrappedChatComponent.fromText(suffixBuilder.toString()));
            });
        });
    }

    @Override
    public void onDisable() {
        wrapper.getProtocolService().ifPresent(protocol -> protocol.removePacketListener(LISTENER_ID));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
