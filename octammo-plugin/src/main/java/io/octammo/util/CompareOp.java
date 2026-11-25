package io.octammo.util;

/** Comparison used by requirements: numbers when both sides are numeric, text otherwise. */
public final class CompareOp {
    private CompareOp() {}

    public static boolean isOperator(String op) {
        return switch (op) {
            case ">=", "<=", ">", "<", "=", "==", "!=" -> true;
            default -> false;
        };
    }

    public static boolean compare(String left, String op, String right) {
        Double l = number(left);
        Double r = number(right);
        if (l != null && r != null) {
            int c = Double.compare(l, r);
            return switch (op) {
                case ">=" -> c >= 0;
                case "<=" -> c <= 0;
                case ">" -> c > 0;
                case "<" -> c < 0;
                case "=", "==" -> c == 0;
                case "!=" -> c != 0;
                default -> false;
            };
        }
        String a = left.trim();
        String b = right.trim();
        return switch (op) {
            case "=", "==" -> a.equalsIgnoreCase(b);
            case "!=" -> !a.equalsIgnoreCase(b);
            default -> false;
        };
    }

    private static Double number(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
