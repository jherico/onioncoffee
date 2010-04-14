package net.sf.onioncoffee.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexUtil {

    public static final int REGEX_FLAGS = Pattern.UNIX_LINES + Pattern.CASE_INSENSITIVE + Pattern.DOTALL;
    public static final int REGEX_MULTILINE_FLAGS = Pattern.MULTILINE + REGEX_FLAGS;
    /**
     * parses a line by a regular expression and returns the first hit. If the
     * regular expression doesn't fit, it returns the default value
     * 
     * @param s
     *            the line to be parsed
     * @param regex
     *            the parsing regular expression
     * @param default_value
     *            the value to be returned, if teh regex doesn't apply
     * @return either the parsed result or the default_value
     */
    public static String parseStringByRE(String s, String regex, String default_value) {
        Pattern p = Pattern.compile(regex, Pattern.DOTALL + Pattern.MULTILINE + Pattern.CASE_INSENSITIVE + Pattern.UNIX_LINES);
        Matcher m = p.matcher(s);
        if (m.find()) {
            return m.group(1);
        }
        return default_value;
    }

}
