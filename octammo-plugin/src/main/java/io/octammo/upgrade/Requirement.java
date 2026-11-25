package io.octammo.upgrade;

import io.octammo.OctaMMOPlugin;
import io.octammo.api.RequirementType;
import io.octammo.util.CompareOp;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Built in: {@code level >= 30}, {@code permission some.node}, {@code placeholder %player_level% >= 30}.
 * Anything else is looked up in the requirements registered through the API (at test time, so plugins may register late).
 */
public interface Requirement {
    boolean test(Player player);

    String text();

    Pattern LEVEL = Pattern.compile("^level\\s*(>=|<=|>|<|==|=)\\s*(\\d+)$", Pattern.CASE_INSENSITIVE);
    Pattern PLACEHOLDER = Pattern.compile("^placeholder\\s+(\\S+)\\s*(>=|<=|>|<|==|=|!=)\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    static Requirement parse(OctaMMOPlugin plugin, String line) {
        String text = line.trim();
        Matcher level = LEVEL.matcher(text);
        if (level.matches()) {
            String op = level.group(1);
            String value = level.group(2);
            return new Simple(text, p -> CompareOp.compare(String.valueOf(p.getLevel()), op, value));
        }
        String[] parts = text.split("\\s+", 2);
        String head = parts[0].toLowerCase(Locale.ROOT);
        String args = parts.length > 1 ? parts[1] : "";
        if (head.equals("permission")) {
            if (args.isBlank()) throw new IllegalArgumentException("permission needs a node: '" + text + "'");
            return new Simple(text, p -> p.hasPermission(args.trim()));
        }
        Matcher ph = PLACEHOLDER.matcher(text);
        if (head.equals("placeholder")) {
            if (!ph.matches()) throw new IllegalArgumentException("use: placeholder %x% >= value, got '" + text + "'");
            String key = ph.group(1);
            String op = ph.group(2);
            String value = ph.group(3);
            return new Simple(text, p -> plugin.papi() != null
                    && CompareOp.compare(plugin.papi().apply(p, key), op, plugin.papi().apply(p, value)));
        }
        // custom requirement from another plugin, resolved when it is tested
        return new Requirement() {
            @Override
            public boolean test(Player player) {
                RequirementType type = plugin.registry().requirement(head);
                return type != null && type.test(player, args);
            }

            @Override
            public String text() {
                RequirementType type = plugin.registry().requirement(head);
                return type == null ? text : type.describe(args);
            }
        };
    }

    record Simple(String text, java.util.function.Predicate<Player> check) implements Requirement {
        @Override
        public boolean test(Player player) {
            return check.test(player);
        }
    }
}
