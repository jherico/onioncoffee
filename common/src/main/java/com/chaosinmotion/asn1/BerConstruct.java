/*  BerConstruct.java
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Represents a constructed object. A constructed object is a collection of other
 * BerNode objects.
 */
public abstract class BerConstruct extends BerNode
{
    private ArrayList<BerNode>   fList;

    protected BerConstruct(int tag)
    {
        super(tag);
        
        fList = new ArrayList<BerNode>();
    }
    
    /**
     * Read the construct into memory from the input stream
     * @param tag The tag used to define this element
     * @param state The current read-state we're in
     * @param parser The parser that is being used to parse this ASN.1 stream
     * @param stream The ASN.1 stream being parsed
     * @throws IOException 
     */
    public BerConstruct(int tag, int state, BerParser parser, BerInputStream stream) throws IOException
    {
        this(tag);
        
        int readTag;
        ReadSequence seq = new ReadSequence(stream);
        while (0 != (readTag = seq.readBerTag())) {
            fList.add(parser.read(readTag, state, stream));
        }
    }

    /**
     * Write the element out. This will use either a definite length (BER/DER)
     * or an indefinite length (CER) encoding mechanism depending on the
     * output format specified in the BerOutputStream object.
     * @param stream
     * @throws IOException 
     * @see com.chaosinmotion.asn1.BerNode#writeElement(com.chaosinmotion.asn1.BerOutputStream)
     */
    public void writeElement(BerOutputStream stream) throws IOException
    {
        stream.writeBerTag(getTag() | Tag.CONSTRUCTED);
        
        if (stream.getEncodingMethod() == BerOutputStream.ENCODING_CER) {
            /*
             * Write this as an indefinite length
             */
            stream.writeBerLength(-1);
            for (BerNode node : fList) {
                node.writeElement(stream);
            }
            stream.writeBerTag(Tag.EOFTYPE);
            stream.writeBerLength(0);
        } else {
            /*
             * Write as definite length
             */
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BerOutputStream tmp = new BerOutputStream(baos,stream.getEncodingMethod());
            for (BerNode node : fList) {
                node.writeElement(tmp);
            }
            tmp.close();
            baos.close();
            
            byte[] data = baos.toByteArray();
            stream.writeBerLength(data.length);
            stream.write(data);
        }
    }

    /**
     * Add a BerNode object to this object
     * @param o
     * @return
     */
    public boolean add(BerNode o)
    {
        return fList.add(o);
    }

    /**
     * Clear this constructed object
     */
    public void clear()
    {
        fList.clear();
    }

    /**
     * Get the node entry by index
     * @param index
     * @return
     */
    public BerNode get(int index)
    {
        return (BerNode)fList.get(index);
    }

    /**
     * Returns true if this is empty
     * @return
     */
    public boolean isEmpty()
    {
        return fList.isEmpty();
    }

    /**
     * Return an iterator that iterates through the contents of this object
     * @return
     */
    public Iterator<BerNode> iterator()
    {
        return fList.iterator();
    }

    /**
     * Remove the specified node
     * @param o The node to remove
     * @return
     */
    public boolean remove(BerNode o)
    {
        return fList.remove(o);
    }

    /**
     * Return the number of elements in this object
     * @return
     */
    public int size()
    {
        return fList.size();
    }

    /**
     * Return the contents of this object as an array
     * @return
     */
    public BerNode[] toArray()
    {
        return (BerNode[])fList.toArray(new BerNode[fList.size()]);
    }
    
    public String toLabeledString(String name)
    {
        StringBuffer buffer = new StringBuffer();
        
        buffer.append(name).append("(").append(Tag.toString(getTag())).append(")=[");
        Iterator<BerNode> it = fList.iterator();
        while (it.hasNext()) {
            buffer.append('\n');
            buffer.append(it.next().toString());
            if (it.hasNext()) {
                buffer.append(';');
            }
        }
        buffer.append(']');
        
        return buffer.toString();
    }
}


