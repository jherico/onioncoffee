package net.sf.onioncoffee.common;

/*
 * LZW.java
 *
 * Created on 01 Dec 2005
 *
 * Implementation of LZW compression/decompression algorithm
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Moshe Fresko
 * @course Algorithmic Programming 1
 * @exercise 3
 */

public class LZW {
    public static byte[] decompress(byte[] input) {
        if (input == null || input.length < 1) {
            return new byte[] {};
        }

        List<Byte> retVal = new ArrayList<Byte>();
        List<Byte> oldCode = new ArrayList<Byte>();
        oldCode.add(input[0]);
        for (int index = 1; index < input.length; ++index) {
            List<Byte> newCode = new ArrayList<Byte>();
        }
        byte[] byteRetVal = new byte[retVal.size()];
        for (int i = 0; i < retVal.size(); ++i) {
            byteRetVal[i] = retVal.get(i);
        }
    }

    public static final int MAGIC_1 = 0x1f;
    public static final int MAGIC_2 = 0x9D;
    public static final int BIT_MASK = 0x1f;
    public static final int BLOCK_MODE = 0x80;
    public static final int FIRST = 257;

    static int input_bit_count = 0;
    static long input_bit_buffer = 0L;
    static long mask = 0;
    static int n_bits_prev = 0;

    /* Helper for input_code() */
    private static long mask(int n_bits) {
        if (n_bits_prev == n_bits)
            return mask;
        n_bits_prev = n_bits;
        mask = 0;
        for (int i = 0; i < n_bits; i++) {
            mask <<= 1;
            mask |= 1;
        }
        return mask;
    }

    private static long input_code(InputStream is, int n_bits) throws IOException {
        int c;
        long return_value;

        while (input_bit_count < n_bits) {
            if ((c = is.read()) < 0) {
                return Long.MAX_VALUE;
            }
            input_bit_buffer |= c << input_bit_count;
            input_bit_count += 8;
        }

        return_value = input_bit_buffer & mask(n_bits);
        input_bit_buffer >>= n_bits;
        input_bit_count -= n_bits;
        return return_value;
    }

    public static void decompress(InputStream is, OutputStream os) {
        long next_code;
        long new_code;
        long old_code;
        int character;
        int counter;
        byte[] string;

        int     n_bits;
        int     block_mode;

        if (is.read() != MAGIC_1) return;
        if (is.read() != MAGIC_2) return;
        character = is.read();
        n_bits = character & BIT_MASK;
        block_mode = character & BLOCK_MODE;
        next_code=FIRST;           
        counter=0;               
        old_code=input_code(is,n_bits);  /* Read in the first code, initialize the */
        character=(int) old_code;          /* character variable, and send the first */
        os.write(character);
        /*
        **  This is the main expansion loop.  It reads in characters from the LZW file
        **  until it sees the special code used to inidicate the end of the data.
        */
        while ((new_code=input_code(is,n_bits)) != Long.MAX_VALUE) {
            if (++counter % 1000 == 0) {
                System.out.println("*");
            }
            /*
            ** This code checks for the special STRING+CHARACTER+STRING+CHARACTER+STRING
            ** case which generates an undefined code.  It handles it by decoding
            ** the last code, and adding a single character to the end of the decode string.
            */
            if (new_code>=next_code)
            {
              *decode_stack=character;
              string=decode_string(decode_stack+1,old_code);
            }
        /*
        ** Otherwise we do a straight decode of the new code.
        */
            else
              string=decode_string(decode_stack,new_code);
        /*
        ** Now we output the decoded string in reverse order.
        */
            character=*string;
            while (string >= decode_stack)
              putc(*string--,output);
        /*
        ** Finally, if possible, add a new code to the string table.
        */
            if (next_code <= MAX_CODE)
            {
              prefix_code[next_code]=old_code;
              append_character[next_code]=character;
              next_code++;
            }
            old_code=new_code;
          }
          printf("\n");

    }
}
