package core;

import utils.HashUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

public class GitObject {
    /**
     * Reads a file and prepends the strict Git blob header.
     * Git format: "blob <size_in_bytes>\0<original_file_contents>"
     * The null byte ('\0') is critical as it separates the metadata header from the actual payload.
     *
     * @param filename The path to the file to be converted into a blob.
     * @return A byte array containing the header and file contents combined.
     */
    public static byte[] createBlob(String filename) {
        Path filePath = Paths.get(filename);
        byte[] blob = null;
        try {
            byte[] byteFile = Files.readAllBytes(filePath);
            String filesize = String.valueOf(Files.size(filePath));

            String header = "blob " + filesize + '\0';
            byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

            // Create a new array large enough to hold both the header and the file payload
            blob = new byte[headerBytes.length + byteFile.length];

            System.arraycopy(headerBytes, 0, blob, 0, headerBytes.length);
            System.arraycopy(byteFile, 0, blob, headerBytes.length, byteFile.length);
        } catch (IOException e) {
            System.out.println("unable to read file " + e.getMessage());
        }
        return blob;
    }


    /**
     * Generates the plain text payload for a Commit object following strict Git formatting rules.
     */
    public static String getCommitText(String treeHash, String message, String parentHash) {
        long currentTime = System.currentTimeMillis() / 1000;

        StringBuilder sb = new StringBuilder();

        sb.append("tree ").append(treeHash).append("\n");
        if(parentHash != null) {
            sb.append("parent ").append(parentHash).append("\n");
        }

        // Git strictly requires emails to be enclosed in angle brackets < >
        sb.append("author User <user@example.com> ").append(currentTime).append(" +0000\n");
        sb.append("committer User <user@example.com> ").append(currentTime).append(" +0000\n");
        sb.append("\n");

        // Commit messages should end with a trailing newline
        sb.append(message).append("\n");

        return sb.toString();
    }


    /**
     * Formats a single file entry to be inserted into a Tree object.
     * Git Tree Entry Format: "[mode] [filename]\0[20-byte binary hash]"
     */
    public static byte[] getFileHexBytes(Path p) {
        String mode = "100644 "; // Standard file permissions mode in Git
        String filename = p.getFileName().toString();
        String header = mode + filename + '\0';

        byte[] blob = createBlob(p.toString());
        String blobHash = HashUtils.generateHexString(blob);
        saveGitObjectToDisk(blobHash, blob);

        // Git trees store the object hash as 20 raw binary bytes, NOT a 40-character text string
        byte[] fileHashByte = HexFormat.of().parseHex(blobHash);

        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            baos.write(header.getBytes());
            baos.write(fileHashByte);
            return baos.toByteArray();
        } catch(IOException e) {
            System.out.println("Error combining header with blob. " + e.getMessage());
        }
        return null;
    }


    /**
     * Safely combines a list of binary Tree entries into a single continuous byte stream.
     */
    public static byte[] combineTreeEntries(List<byte[]> treeEntries) {
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for(byte[] b : treeEntries) {
                baos.write(b);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            System.out.println("Error combining tree entries. " + e.getMessage());
        }
        return null;
    }

    /**
     * Compresses and saves a Git object to the `.minigit/objects` directory.
     * Git splits the 40-character SHA-1 hash into two parts to optimize file system lookups:
     * - The first 2 characters become the directory name.
     * - The remaining 38 characters become the file name.
     */
    public static void saveGitObjectToDisk(String hexString, byte[] rawData) {
        String folderName = hexString.substring(0, 2);
        String fileName   = hexString.substring(2, 40);
        Path objectPath = Paths.get(".minigit", "objects", folderName, fileName);

        try {
            Files.createDirectories(objectPath.getParent());

            // DeflaterOutputStream applies the zlib compression required by Git
            try(FileOutputStream fos = new FileOutputStream(objectPath.toFile());
                DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {
                dos.write(rawData);
            }
        } catch (IOException e) {
            System.out.println("Error saving to disk. " + e.getMessage());
        }
    }
}
