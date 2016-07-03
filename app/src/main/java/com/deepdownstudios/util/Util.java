package com.deepdownstudios.util;

/**
 * Created by davidp on 6/29/16.
 */
public class Util {
    public static void Assert(boolean isTrue) {
        if(!isTrue)
            throw new AssertionError("Assertion Failed.");
    }

    public static void Assert(boolean isTrue, String message) {
        if(!isTrue)
            throw new AssertionError("Assertion Failed : " + message);
    }
}
