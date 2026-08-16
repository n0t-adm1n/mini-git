package utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

public class FileUtils {
    /**
     * Reads a custom `.mini-gitignore` file to avoid tracking unneeded files/folders.
     */
    public static Set<String> getIgnoreSet() {
        Set<String> set = new HashSet<>();
        Path ignoreFile = Paths.get(".mini-gitignore");

        if(!Files.exists(ignoreFile)) throw new RuntimeException("Create the .mini-gitignore file");

        try(Stream<String> dirAndFilesToIgnoreStream = Files.lines(ignoreFile);) {
            for(String s : dirAndFilesToIgnoreStream.toList()) {
                String st = s.trim();

                if(st.isEmpty()) continue;

                // Safely strip trailing slashes so directory names match strictly (e.g. ".idea" not ".idea/")
                if(st.endsWith("/")) st = st.substring(0, st.length()-1);
                set.add(st);
            }
        } catch (IOException e) {
            System.out.println("cannot read .mini-gitignore file " + e.getMessage());
        }
        return set;
    }

    public static void clearWorkingDirectory(Path path, Set<String> ignoreSet) {

        try(Stream<Path> files = Files.list(path)) {
            List<Path> pathList = files.toList();

            for(Path p : pathList) {
                String filename = p.getFileName().toString();

                if(ignoreSet.contains(filename)) {
                    continue;
                }

                if(Files.isDirectory(p)) {
                    // recursively remove everything inside the directory
                    clearWorkingDirectory(p, ignoreSet);
                    Files.delete(p);
                } else if(Files.isRegularFile(p)) {
                    Files.delete(p);
                } else {
                    System.out.println(filename + " is something else");
                }


            }
        } catch (IOException e) {
            System.out.println("error occurred while clearing directory. " + e.getMessage());
        }

    }

    public static byte[] decompressZlibFile(Path path) throws IOException {
        try (
                FileInputStream fis = new FileInputStream(path.toFile());
                InflaterInputStream iis = new InflaterInputStream(fis)
        ) {
            // Return the entire decompressed file as raw bytes
            return iis.readAllBytes();
        }
    }
}
