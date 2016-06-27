package de.max.miband.models;

import java.util.Arrays;

public class ActivityKind {
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_ACTIVITY = 1;
    public static final int TYPE_LIGHT_SLEEP = 2;
    public static final int TYPE_DEEP_SLEEP = 4;
    public static final int TYPE_SLEEP = TYPE_LIGHT_SLEEP | TYPE_DEEP_SLEEP;
    public static final int TYPE_ALL = TYPE_ACTIVITY | TYPE_SLEEP;

    public static byte[] mapToDBActivityTypes(int types) {
        byte[] result = new byte[3];
        int i = 0;
        if ((types & ActivityKind.TYPE_ACTIVITY) != 0) {
            result[i++] = ActivityData.TYPE_ACTIVITY;
        }
        if ((types & ActivityKind.TYPE_DEEP_SLEEP) != 0) {
            result[i++] = ActivityData.TYPE_DEEP_SLEEP;
        }
        if ((types & ActivityKind.TYPE_LIGHT_SLEEP) != 0) {
            result[i++] = ActivityData.TYPE_LIGHT_SLEEP;
        }
        return Arrays.copyOf(result, i);
    }

}
