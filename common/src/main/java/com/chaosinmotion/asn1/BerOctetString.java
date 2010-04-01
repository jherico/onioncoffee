/*  BerBoolean.java
 *
 *  Created on Jun 2, 2006 by William Edward Woody
 */

/*
 * Copyright 2007 William Woody, All Rights Reserved.
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

/**
 * Represents an octet string, or a string made up of 8-bit characters. No encoding
 * is imposed here, as in general most printable strings in the ASN-1 encoding
 * set do not specify a character set. Generally strings are transmitted as
 * octet strings using UTF-8 encoding.
 */
public class BerOctetString extends BerNode
{
    private byte[] fValue;

    /**
     * Construct a new boolean object with the specified tag
     * @param tag
     * @param value
     */
    public BerOctetString(int tag, byte[] value)
    {
        super(tag);
        fValue = value;
    }
    
    /**
     * Construct a boolean of type BOOLEAN
     * @param value
     */
    public BerOctetString(byte[] value)
    {
        this(Tag.OCTETSTRING,value);
    }
    
    /**
     * Construct a boolean from the input stream
     * @param tag
     * @param stream
     * @throws IOException
     */
    public BerOctetString(int tag, BerInputStream stream) throws IOException
    {
        super(tag);
        
        fValue = stream.readOctetString(0 == (tag & Tag.CONSTRUCTED));
    }

    /**
     * Write this BER element to the output stream
     * Comment
     * @param stream
     * @throws IOException
     * @see com.chaosinmotion.asn1.BerNode#writeElement(com.chaosinmotion.asn1.BerOutputStream)
     */
    public void writeElement(BerOutputStream stream) throws IOException
    {
        stream.writeBerTag(getTag() | (stream.isComplexOctetString(fValue.length) ? Tag.CONSTRUCTED : 0));
        stream.writeOctetString(fValue,0,fValue.length);
    }

    /**
     * Return the value of this boolean object
     * @return
     */
    public byte[] getValue()
    {
        return fValue;
    }

    public String toString()
    {
        return "BerOctetString(" + Tag.toString(getTag()) + ")=" + fValue;
    }
}


