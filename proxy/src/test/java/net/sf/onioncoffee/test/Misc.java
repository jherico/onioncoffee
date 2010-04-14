package net.sf.onioncoffee.test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.onioncoffee.common.Encoding;
import net.sf.onioncoffee.common.RegexUtil;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class Misc {

    public static void findUniqueFlags() throws IOException {
        String desc = Files.toString(new File("C:\\Documents and Settings\\bdavis\\.jtor\\consensus"), Charsets.UTF_8);
        Pattern p = Pattern.compile("^s (.*?)$", RegexUtil.REGEX_MULTILINE_FLAGS);
        Matcher m = p.matcher(desc);
        Set<String> flags = new HashSet<String>();
        while (m.find()) {
            for (String flag : m.group(1).split("\\s")) {
                flags.add(flag);
            }
        }
        for (String s : flags) {
            System.out.println(s);
        }
    }
    
    public static void main(String[] args) throws IOException {
        String a="8QRkut08j35Bb0TN1JDe8MCz96c";
        String b = "lohTxA/HTpzmFSy9AvswRN9e3Gw";
        System.out.println(Encoding.toHexStringNoColon(Encoding.parseBase64(a)));
        System.out.println(Encoding.toHexStringNoColon(Encoding.parseBase64(b)));
//        findUniqueFlags();
    }
}
