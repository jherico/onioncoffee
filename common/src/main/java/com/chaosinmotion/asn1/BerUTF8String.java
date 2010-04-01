/*  BerUTF8String.java
 *
 *  Created on August 14, 2008 by William Edward Woody
 */
/*
 * Copyright 2007, 2008 William Woody, All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list 
 * of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this 
 * list of conditions and the following disclaimer in the documentation and/or other 
 * materials provided with the distribution.
 * 
 * 3. Neither the name of Chaos In Motion nor the names of its contributors may be used 
 * to endorse or promote products derived from this software without specific prior 
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY 
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES 
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT 
 * SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED 
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR 
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH 
 * DAMAGE.
 * 
 * Contact William Woody at woody@alumni.caltech.edu or at woody@chaosinmotion.com. 
 * Chaos In Motion is at http://www.chaosinmotion.com
 */

package com.chaosinmotion.asn1;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Represents a UTF8 String string, which is (as far as I'm concerned) an arbitrary
 * array of 8-bit bytes
 */
public class BerUTF8String extends BerOctetString
{
    public BerUTF8String(int tag, byte[] value)
    {
        super(tag, value);
    }

    public BerUTF8String(byte[] value)
    {
        this(Tag.UTF8STRING,value);
    }

    public BerUTF8String(int tag, BerInputStream stream) throws IOException
    {
        super(tag, stream);
    }

    public String toString()
    {
        return "BerUTF8String(" + Tag.toString(getTag()) + ")=" + getValue();
    }
    
    public String getStringValue()
    {
        try {
            return new String(getValue(),"UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            return "";      // should always be supported
        }
    }
}


