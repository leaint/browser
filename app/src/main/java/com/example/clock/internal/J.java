package com.example.clock.internal;

import androidx.annotation.NonNull;

/**
 * android FastNative function
 */
public class J {

    @NonNull
    public static String repeat(@NonNull String s, int n) {
        return s.repeat(n);
    }

    @NonNull
    public static String concat(@NonNull String a, @NonNull String b) {
        return a.concat(b);
    }

    public static boolean endsWith(@NonNull String a, @NonNull String suffix) {
        int pc = suffix.length();
        int toffset = a.length() - pc;
        // Note: toffset might be near -1>>>1.
        if (toffset < 0) {
            return false;
        } else if (pc > 2) {
            // head
            if (a.charAt(toffset) != suffix.charAt(0) || a.charAt(toffset + 1) != suffix.charAt(1)) {
                return false;
            }
        }

        while (--pc >= 0) {
            if (a.charAt(toffset + pc) != suffix.charAt(pc)) {
                return false;
            }
        }
        return true;
    }

    public static boolean startsWith(@NonNull String a, @NonNull String prefix) {
        int pc = prefix.length();
        int ac = a.length();
        // Note: toffset might be near -1>>>1.
        if (ac - pc < 0) {
            return false;
        } else if (pc > 13) {
            /* https://www.
             *             ^ = 12
             *
             */
            if (a.charAt(12) != prefix.charAt(12)) {
                return false;
            }

        } else if (pc > 2) {
            // tail
            if (a.charAt(pc - 1) != prefix.charAt(pc - 1)) {
                return false;
            }
        }

        int po = 0;
        while (--pc >= 0) {
            if (a.charAt(po) != prefix.charAt(po)) {
                return false;
            }
            po++;
        }
        return true;
    }
}