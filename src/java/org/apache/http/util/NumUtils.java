package org.apache.http.util;

import org.apache.http.io.CharArrayBuffer;

public class NumUtils {

    private NumUtils() {
    }
    
    public static int parseUnsignedInt(
            final CharArrayBuffer buffer, final int indexFrom, final int indexTo,
            int radix) 
            throws NumberFormatException {
        if (buffer == null) {
            throw new IllegalArgumentException("Char array buffer may not be null");
        }
        if (indexFrom < 0) {
            throw new IndexOutOfBoundsException();
        }
        if (indexTo > buffer.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (indexFrom > indexTo) {
            throw new IndexOutOfBoundsException();
        }
        if (radix < Character.MIN_RADIX) {
            throw new IllegalArgumentException ("Radix may not be less than Character.MIN_RADIX");
        }
        if (radix > Character.MAX_RADIX) {
            throw new IllegalArgumentException ("Radix may not be greater than Character.MAX_RADIX");
        }
        int i1 = indexFrom;
        int i2 = indexTo;
        while (i1 < indexTo && Character.isWhitespace(buffer.charAt(i1))) {
            i1++;
        }
        while (i2 > i1 && Character.isWhitespace(buffer.charAt(i2 - 1))) {
            i2--;
        }
        if (i1 == i2) {
            throw new NumberFormatException("Empty input");
        }
        int digit = Character.digit(buffer.charAt(i1++), radix);        
        if (digit < 0) {
            throw new NumberFormatException("Invalid unsigned integer: " + 
                    buffer.substring(indexFrom, indexTo));
        }
        int num = digit;
        while (i1 < i2) {
            digit = Character.digit(buffer.charAt(i1++), radix);        
            if (digit < 0) {
                throw new NumberFormatException("Invalid unsigned integer: " + 
                        buffer.substring(indexFrom, indexTo));
            }
            num *= radix;
            num += digit;
            if (num < 0) {
                throw new NumberFormatException("Unsigned integer too large: " + 
                        buffer.substring(indexFrom, indexTo));
            }
        }
        return num;
    }

    public static int parseUnsignedInt(
            final CharArrayBuffer buffer, final int indexFrom, final int indexTo) 
            throws NumberFormatException {
        return parseUnsignedInt(buffer, indexFrom, indexTo, 10);
    }
    
    public static int parseUnsignedHexInt(
            final CharArrayBuffer buffer, final int indexFrom, final int indexTo) 
            throws NumberFormatException {
        return parseUnsignedInt(buffer, indexFrom, indexTo, 16);
    }
    
    public static int parseUnsignedBinInt(
            final CharArrayBuffer buffer, final int indexFrom, final int indexTo) 
            throws NumberFormatException {
        return parseUnsignedInt(buffer, indexFrom, indexTo, 2);
    }
    
}
