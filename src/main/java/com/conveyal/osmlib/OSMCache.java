package com.conveyal.osmlib;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;

/**
 * Created by matthewc on 7/18/16.
 */
public class OSMCache {
    public final String bucket;
    public final File cacheDir;

    private AmazonS3 s3 = new AmazonS3Client();

    public OSMCache (String bucket, File cacheDir) {
        this.bucket = bucket;
        this.cacheDir = cacheDir;
    }

    private Cache<String, OSM> osmCache = CacheBuilder.newBuilder()
            .maximumSize(10)
            .build();

    /** Store data in the OSM cache */
    public OSM put (String id, File osmFile) throws IOException {
        String cleanId = cleanId(id);

        OSM ret = new OSM(null);
        ret.intersectionDetection = true;
        // TODO I think this may break with multiple OSM files in the same directory.
        ret.readFromFile(osmFile.getAbsolutePath());

        File cacheFile = new File(cacheDir, cleanId + ".pbf");

        if (osmFile.getName().endsWith(".pbf")) {
            Files.copy(osmFile.toPath(), cacheFile.toPath());
        } else {
            OutputStream out = new BufferedOutputStream(new FileOutputStream(cacheFile));
            ret.writePbf(out);
            out.close();
        }

        if (bucket != null) {
            s3.putObject(bucket, cleanId + ".pbf", cacheFile);
        }

        osmCache.put(id, ret);
        return ret;
    }

    public OSM get (String id) {
        try {
            return osmCache.get(id, () -> {
                String cleanId = cleanId(id);
                File cacheFile = new File(cacheDir, cleanId + ".pbf");

                if (!cacheFile.exists()) {
                    // fetch from S3
                    s3.getObject(new GetObjectRequest(bucket, cleanId + ".pbf"), cacheFile);
                }

                OSM ret = new OSM(null);
                ret.intersectionDetection = true;
                ret.readFromFile(cacheFile.getAbsolutePath());

                return ret;
            });
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public String cleanId(String id) {
        return id.replaceAll("[^A-Za-z0-9]", "-");
    }
}
