package io.octammo.api;

/** TYPE and ID of an MMOItems template (both upper case). */
public record MmoItemRef(String type, String id) {
    public String key() {
        return type + ":" + id;
    }
}
