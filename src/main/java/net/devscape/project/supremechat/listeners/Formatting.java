package net.devscape.project.supremechat.listeners;

import net.devscape.project.supremechat.SupremeChat;
import net.devscape.project.supremechat.utils.FormatUtil;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.devscape.project.supremechat.utils.Message.*;

public class Formatting implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        if (e.isCancelled()) return;

        if (SupremeChat.getInstance().getConfig().getBoolean("mute-chat")) {
            if (!player.hasPermission(Objects.requireNonNull(SupremeChat.getInstance().getConfig().getString("bypass-mute-chat-permission")))) {
                msgPlayer(player, "&cChat is currently muted!");
                e.setCancelled(true);
                return;
            }
        }

        // BANNED WORD DETECTION
        if (!player.hasPermission("sc.bypass")) {
            for (String word : SupremeChat.getInstance().getConfig().getStringList("banned-words")) {
                if (isWordBlocked(e.getMessage(), word)) {
                    e.setCancelled(true);
                    String detect = SupremeChat.getInstance().getConfig().getString("word-detect");
                    detect = detect.replaceAll("%word%", word);

                    msgPlayer(player, detect);

                    // alert staff
                    for (Player staff : Bukkit.getOnlinePlayers()) {
                        if (staff.hasPermission(SupremeChat.getInstance().getConfig().getString("detect-alert-staff-permission"))) {
                            String detect_alert = SupremeChat.getInstance().getConfig().getString("word-detect-staff");
                            detect_alert = detect_alert.replaceAll("%message%", e.getMessage());
                            detect_alert = detect_alert.replaceAll("%name%", player.getName());

                            msgPlayer(staff, detect_alert);
                            break;
                        }
                    }
                }
            }


            // CHAT DELAY
            if (SupremeChat.getInstance().getConfig().getInt("chat-delay") >= 1) {
                if (!SupremeChat.getInstance().getChatDelayList().contains(player)) {
                    SupremeChat.getInstance().getChatDelayList().add(player);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            SupremeChat.getInstance().getChatDelayList().remove(player);
                        }
                    }.runTaskLaterAsynchronously(SupremeChat.getInstance(), 20L * SupremeChat.getInstance().getConfig().getInt("chat-delay"));
                } else {
                    e.setCancelled(true);
                    msgPlayer(player, SupremeChat.getInstance().getConfig().getString("chat-warn"));
                }
            }


            // REPEAT FILTER
            if (SupremeChat.getInstance().getConfig().getBoolean("repeat-enable")) {
                if (SupremeChat.getInstance().getLastMessage().containsKey(player)) {
                    String lastMessage = SupremeChat.getInstance().getLastMessage().get(player);
                    String newMessage = e.getMessage();

                    if (newMessage.contains(lastMessage)) {
                        e.setCancelled(true);
                        msgPlayer(player, SupremeChat.getInstance().getConfig().getString("repeat-warn"));
                    } else {
                        SupremeChat.getInstance().getLastMessage().remove(player);
                        SupremeChat.getInstance().getLastMessage().put(player, newMessage);
                    }
                } else {
                    String newMessage = e.getMessage();
                    SupremeChat.getInstance().getLastMessage().put(player, newMessage);
                }
            }


            // CAPS FILTER
            if (SupremeChat.getInstance().getConfig().getBoolean("caps-lowercase")) {
                if (e.getMessage().chars().filter(Character::isUpperCase).count() >= SupremeChat.getInstance().getConfig().getInt("caps-limit")) {
                    for (final char c : e.getMessage().toCharArray()) {
                        if (Character.isUpperCase(c)) {
                            if (!SupremeChat.getInstance().getConfig().getBoolean("disable-caps-warn")) {
                                msgPlayer(player, SupremeChat.getInstance().getConfig().getString("caps-warn"));
                            }
                            e.setMessage(format(e.getMessage().toLowerCase()));
                            break;
                        }
                    }
                }
            }
        }


        // ITEM IN CHAT
        ItemStack item = player.getInventory().getItemInMainHand();
        boolean itemChat = SupremeChat.getInstance().getConfig().getBoolean("enable-chat-item");
        String replacement = SupremeChat.getInstance().getConfig().getString("chat-item-replace");
        assert replacement != null;

        if (item.getItemMeta() != null) {
            String displayName = item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
            replacement = replacement.replaceAll("%item%", format("x" + item.getAmount() + " " + displayName));
        } else {
            replacement = replacement.replaceAll("%item%", format("x" + item.getAmount() + " " + item.getType().name()));
        }

        String message = e.getMessage();

        if (itemChat) {
            for (String itemString : SupremeChat.getInstance().getConfig().getStringList("chat-item-strings")) {
                if (message.contains(itemString)) {
                    e.setMessage(message.replace(itemString, format(replacement)));
                    break;
                }
            }
        }

        /// CHAT FORMATTING


        boolean enableChatFormat = SupremeChat.getInstance().getConfig().getBoolean("enable-chat-format");
        String originalMessage = e.getMessage();
        String chat = originalMessage;

        if (enableChatFormat) {
            boolean grouping = SupremeChat.getInstance().getConfig().getBoolean("group-formatting");
            String rank;

            if (grouping) {
                rank = FormatUtil.getRank(player);
                if (getRankFormat(rank) != null) {
                    chat = getRankFormat(rank);
                } else {
                    chat = getGlobalFormat();
                }
            } else {
                chat = getGlobalFormat();
            }

            chat = addChatPlaceholders(chat, player, originalMessage);
        }

        boolean hover = SupremeChat.getInstance().getConfig().getBoolean("hover.enable");
        boolean click = SupremeChat.getInstance().getConfig().getBoolean("click.enable");

        List<String> hoverMessages = new ArrayList<>();

        for (String hoverMessage : SupremeChat.getInstance().getConfig().getStringList("hover.string")) {
            hoverMessage = addOtherPlaceholders(hoverMessage, player);
            hoverMessages.add(hoverMessage);
        }

        String permission = SupremeChat.getInstance().getConfig().getString("chat-color-permission");

        TextComponent msg = new TextComponent(TextComponent.fromLegacyText(format(chat)));

        if (hover) {
            setHoverBroadcastEvent(msg, hoverMessages, player);
        }

        if (click) {
            String clickMsg = SupremeChat.getInstance().getConfig().getString("click.string");
            clickMsg = addOtherPlaceholders(clickMsg, player);
            setClickBroadcastEvent(msg, clickMsg, player);
        }

        String formattedMessage = TextComponent.toLegacyText(msg);

        if (permission != null && !player.hasPermission(permission)) {
            // Remove chat color codes if player doesn't have the permission
            formattedMessage = formattedMessage.replaceAll("&([0-9a-fA-Fk-oK-OrR])", "");
        }

        formattedMessage = formattedMessage.replaceAll("%", "%%"); // Escape the % character

        e.setFormat(formattedMessage);
    }

    private static boolean isWordBlocked(String message, String blockedWord) {
        String pattern = "\\b" + blockedWord + "\\b";
        Pattern regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        Matcher matcher = regex.matcher(message);

        return matcher.find();
    }
}