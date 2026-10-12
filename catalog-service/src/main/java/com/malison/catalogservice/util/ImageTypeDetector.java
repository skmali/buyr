package com.malison.catalogservice.util;

/**
 * Sniffs an image's real format from its magic bytes. Used anywhere we
 * receive image bytes (upload or AI-generated) so we never trust a
 * caller-supplied Content-Type header alone.
 */
public final class ImageTypeDetector {

    private ImageTypeDetector() {
    }

    /** @return the detected content type ("image/png", "image/jpeg", "image/webp"), or null if none match. */
    public static String detect(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)) {
            return "image/png";
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (bytes.length >= 12
                && startsWith(bytes, 'R', 'I', 'F', 'F')
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != (signature[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }
}
