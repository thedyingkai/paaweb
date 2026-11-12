package com.cwnupaa.backend.utils;

public final class OjBindingPolicy {
    private OjBindingPolicy() {}
    public static boolean validQojHandle(String handle) {
        return handle == null || handle.isBlank() || handle.trim().matches("[A-Za-z0-9_-]{1,100}");
    }
}
