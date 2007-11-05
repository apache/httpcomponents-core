package org.apache.http.message;

import java.util.NoSuchElementException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.message.BasicHeaderIterator;

/**
 * Tests for {@link BasicHeaderElementIterator}.
 * 
 * @version $Revision$
 * 
 * @author Andrea Selva <selva.andre at gmail.com>
 */
public class TestBasicHeaderElementIterator extends TestCase {
    
    // ------------------------------------------------------------ Constructor
    public TestBasicHeaderElementIterator(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestBasicHeaderElementIterator.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods
    public static Test suite() {
        return new TestSuite(TestBasicHeaderElementIterator.class);
    }

    public void testMultiHeader() {
        Header[] headers = new Header[]{
                new BasicHeader("Name", "value0"),
                new BasicHeader("Name", "value1")
        };
       
        HeaderElementIterator hei = 
                new BasicHeaderElementIterator(
                        new BasicHeaderIterator(headers, "Name"));
        
        assertTrue(hei.hasNext());
        HeaderElement elem = (HeaderElement) hei.next();
        assertEquals("The two header values must be equal", 
                "value0", elem.getName());
        
        assertTrue(hei.hasNext());
        elem = (HeaderElement)hei.next();
        assertEquals("The two header values must be equal", 
                "value1", elem.getName());

        assertFalse(hei.hasNext());
        try {
            hei.next();
            fail("NoSuchElementException should have been thrown");
        } catch (NoSuchElementException ex) {
            // expected
        }

        assertFalse(hei.hasNext());
        try {
            hei.next();
            fail("NoSuchElementException should have been thrown");
        } catch (NoSuchElementException ex) {
            // expected
        }
    }
    
    public void testMultiHeaderSameLine() {
        Header[] headers = new Header[]{
                new BasicHeader("name", "value0,value1"),
                new BasicHeader("nAme", "cookie1=1,cookie2=2")
        };
        
        HeaderElementIterator hei = 
                new BasicHeaderElementIterator(new BasicHeaderIterator(headers, "Name"));
        
        HeaderElement elem = (HeaderElement)hei.next();
        assertEquals("The two header values must be equal", 
                "value0", elem.getName());
        elem = (HeaderElement)hei.next();
        assertEquals("The two header values must be equal", 
                "value1", elem.getName());
        elem = (HeaderElement)hei.next();
        assertEquals("The two header values must be equal", 
                "cookie1", elem.getName());
        assertEquals("The two header values must be equal", 
                "1", elem.getValue());
        
        elem = (HeaderElement)hei.next();
        assertEquals("The two header values must be equal", 
                "cookie2", elem.getName());
        assertEquals("The two header values must be equal", 
                "2", elem.getValue());
    }
    
    public void testFringeCases() {
        Header[] headers = new Header[]{
                new BasicHeader("Name", null),
                new BasicHeader("Name", "    "),
                new BasicHeader("Name", ",,,")
        };
       
        HeaderElementIterator hei = 
                new BasicHeaderElementIterator(
                        new BasicHeaderIterator(headers, "Name"));
        
        assertFalse(hei.hasNext());
        try {
            hei.next();
            fail("NoSuchElementException should have been thrown");
        } catch (NoSuchElementException ex) {
            // expected
        }

        assertFalse(hei.hasNext());
        try {
            hei.next();
            fail("NoSuchElementException should have been thrown");
        } catch (NoSuchElementException ex) {
            // expected
        }
    }
    
}
