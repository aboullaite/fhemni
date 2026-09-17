package dev.maboullaite.fhemni.civic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface CivicPriorityShareImageStorage {

    void put(String objectKey, Path source) throws IOException;

    StoredObject open(String objectKey) throws IOException;

    boolean delete(String objectKey) throws IOException;

    record StoredObject(InputStream content, long size) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            content.close();
        }
    }
}
