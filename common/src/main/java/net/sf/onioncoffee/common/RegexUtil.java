package net.sf.onioncoffee.common;

import java.util.regex.Pattern;

public class RegexUtil {

    public static final int REGEX_FLAGS = Pattern.UNIX_LINES + Pattern.CASE_INSENSITIVE + Pattern.DOTALL;
    public static final int REGEX_MULTILINE_FLAGS = Pattern.MULTILINE + REGEX_FLAGS;

}
