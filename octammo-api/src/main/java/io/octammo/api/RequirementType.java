package io.octammo.api;

import org.bukkit.entity.Player;

/**
 * A custom requirement. In an upgrade file write {@code "<id()> <arguments>"} under {@code requirements:}.
 * Built in: {@code level >= 30}, {@code permission some.node}, {@code placeholder %x% >= 30}.
 */
public interface RequirementType {
    String id();

    boolean test(Player player, String arguments);

    default String describe(String arguments) {
        return id() + " " + arguments;
    }
}
