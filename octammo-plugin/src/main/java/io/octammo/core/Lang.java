package io.octammo.core;

import io.octammo.OctaMMOPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * lang/en.yml (English-only) with fallback to the built-in resource.
 * Text formats: &amp; codes, &amp;#RRGGBB, &amp;x&amp;R&amp;R..., and MiniMessage (text-format: auto | legacy | minimessage).
 */
public final class Lang {
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern MINI_TAG = Pattern.compile("<(/?)(#[0-9a-fA-F]{6}|[a-z_]{3,})(:[^>]*)?>");
    private static final Pattern TOKEN = Pattern.compile("\\{([a-z0-9_.\\-]+)\\}");
    private static final Pattern LIST_TOKEN = Pattern.compile("^\\{([a-z0-9_.\\-]+)\\}$");
    public static final String DEFAULT_PREFIX =
            "&8[&x&F&F&0&0&0&0&lO&x&F&4&0&0&2&B&lc&x&E&9&0&0&5&5&lt&x&D&F&0&0&8&0&la&x&D&4&0&0&A&A&lM&x&C&9&0&0&D&5&lM&x&B&E&0&0&F&F&lO&8]";

    private static volatile String textFormat = "auto";

    private final OctaMMOPlugin plugin;
    private YamlConfiguration messages = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();

    public Lang(OctaMMOPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        textFormat = plugin.getConfig().getString("text-format", "auto").toLowerCase(Locale.ROOT);
        File dir = new File(plugin.getDataFolder(), "lang");
        ConfigMigrator.migrate(plugin, new File(dir, "en.yml"), "lang/en.yml");
        String requested = plugin.getConfig().getString("language", "en").toLowerCase(Locale.ROOT);
        if (!requested.equals("en")) plugin.getLogger().warning("OctaMMO 1.2.0 uses English language files only; using lang/en.yml.");
        File file = new File(dir, "en.yml");
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("lang/en.yml")) {
            if (in != null) {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    fallback = YamlConfiguration.loadConfiguration(r);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not read built-in lang: " + e.getMessage());
        }
    }

    public String prefix() {
        return plugin.getConfig().getString("prefix", DEFAULT_PREFIX);
    }

    public String raw(String key) {
        String v = messages.getString(key);
        if (v == null) v = fallback.getString(key);
        return v == null ? key : v;
    }

    public List<String> rawList(String key) {
        List<String> v = messages.isList(key) ? messages.getStringList(key) : null;
        if (v == null) v = fallback.getStringList(key);
        return v;
    }

    public boolean has(String key) {
        return messages.isString(key) || fallback.isString(key);
    }

    public boolean hasList(String key) {
        return messages.isList(key) || fallback.isList(key);
    }

    /** Raw text with {placeholders} filled in (no colour parsing). */
    public String format(String key, String... kv) {
        return apply(raw(key), kv);
    }

    public Component component(String key, String... kv) {
        return color(expand(format(key, kv), null, null));
    }

    public List<Component> components(String key, String... kv) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(key)) addSplit(out, expand(apply(line, kv), null, null));
        return out;
    }

    /** Sends a message. Messages carry {prefix} themselves where they want it. */
    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(color(expand(format(key, kv), to instanceof Player p ? p : null, null)));
    }

    public void sendList(CommandSender to, String key, String... kv) {
        for (String line : rawList(key)) to.sendMessage(color(expand(apply(line, kv), to instanceof Player p ? p : null, null)));
    }

    /**
     * Expands {lang.key} tokens, then {prefix} and the given {placeholders}, then PlaceholderAPI.
     * Unknown tokens are left untouched.
     */
    public String expand(String text, Player player, Map<String, String> ph) {
        if (text == null) return "";
        Matcher m = TOKEN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String repl = has(key) ? raw(key) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        String out = sb.toString().replace("{prefix}", prefix());
        if (ph != null) {
            for (Map.Entry<String, String> e : ph.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        if (player != null && plugin.papi() != null) out = plugin.papi().apply(player, out);
        return out;
    }

    public Component render(String text, Player player, Map<String, String> ph) {
        return color(expand(text, player, ph));
    }

    /** A line that is exactly one {list.key} expands to the whole list; a value containing a newline becomes several lines. */
    public List<Component> renderLines(List<String> rawLines, Player player, Map<String, String> ph) {
        List<Component> out = new ArrayList<>();
        for (String line : rawLines) {
            Matcher m = LIST_TOKEN.matcher(line.trim());
            if (m.matches() && hasList(m.group(1))) {
                for (String inner : rawList(m.group(1))) addSplit(out, expand(inner, player, ph));
            } else {
                addSplit(out, expand(line, player, ph));
            }
        }
        return out;
    }

    private static void addSplit(List<Component> out, String expanded) {
        for (String part : expanded.split("\n", -1)) out.add(color(part));
    }

    public static Component color(String s) {
        boolean mini = switch (textFormat) {
            case "minimessage" -> true;
            case "legacy" -> false;
            default -> s.indexOf('&') < 0 && MINI_TAG.matcher(s).find();
        };
        Component c = mini ? MINI.deserialize(s) : LEGACY.deserialize(s);
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static String apply(String s, String... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) s = s.replace("{" + kv[i] + "}", kv[i + 1]);
        return s;
    }
}
