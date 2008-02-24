package org.apache.http.nio.entity;

import java.io.IOException;

import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;

/**
 * A listener for available data on a non-blocking {@link ConsumingNHttpEntity}.
 */
public interface ContentListener {

    /**
     * Notification that content is available to be read from the decoder.
     */
    void contentAvailable(ContentDecoder decoder, IOControl ioctrl) throws IOException;

    /**
     * Notification that any resources allocated for reading can be released.
     */
    void finished();

}