/*  BerPrimitive.java
 *
 *  Created on Jun 1, 2006 by William Edward Woody
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
 * The BER Primitive object represents a properly formatted BER object which
 * is unknown, but well formed.
 */
public class BerPrimitive extends BerNode
{
    private byte[] fData;

    /**
     * Construct a new primitive object
     * @param tag
     * @param data
     */
    public BerPrimitive(int tag, byte[] data)
    {
        super(tag);
        fData = data;
    }
    
    /**
     * Construct a new primitive object by reading it in from the input stream
     * @param tag
     * @param stream
     * @throws IOException
     */
    public BerPrimitive(int tag, BerInputStream stream) throws IOException
    {
        super(tag);
        
        fData = new byte[stream.readBerLength()];
        stream.read(fData);
    }
    
    /**
     * Write the element out to the output stream
     * @param stream
     * @throws IOException 
     * @see com.chaosinmotion.asn1.BerNode#writeElement(com.chaosinmotion.asn1.BerOutputStream)
     */

    public void writeElement(BerOutputStream stream) throws IOException
    {
        stream.writeBerTag(getTag());
        stream.writeBerLength(fData.length);
        stream.write(fData);
    }
    
    /**
     * Return the data representation of this primitive
     * @return
     */
    public byte[] getData()
    {
        return fData;
    }

    public String toString()
    {
        return "BerPrimitive(" + Tag.toString(getTag()) + ")=" + fData;
    }
}


