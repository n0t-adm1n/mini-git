import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class Main {
    static void main(String[] args) {
        if(args.length == 0) {
            System.out.println("Usage: java Main <command> [<args>]");
            return;
        }

        String command = args[0];

        // Basic CLI Router to handle different Git commands
        switch (command) {
            case "init" :
                initializeRepository();
                break;

            case "hash-object" :
                boolean writeFlag = false;
                String filename = "";

                // Parse arguments for the -w flag and filename
                for(int i = 1; i < args.length; i++) {
                    if(args[i].equals("-w")) {
                        writeFlag = true;
                    } else {
                        filename = args[i];
                    }
                }

                if(filename.isEmpty()) {
                    System.out.println("please enter a file name");
                    break;
                }

                if(writeFlag) {
                    writeToDisk(filename);
                } else {
                    byte[] blob = createBlob(filename);
                    try {
                        String hexString = generateHexString(blob);
                        System.out.println(hexString);
                    } catch(NullPointerException e) {
                        System.out.println("cannot generate hex string. " + e.getMessage());
                    }
                }
                break;

            case "cat-file" :
                boolean printFlag = false;
                String hash = "";

                // Parse arguments for the -p (print) flag and the object hash
                for(int i = 1; i < args.length; i++) {
                    if(args[i].equals("-p")) {
                        printFlag = true;
                    } else {
                        hash = args[i];
                    }
                }

                if(hash.isEmpty()) {
                    System.out.println("Please provide a hash to read");
                    break;
                }

                if(printFlag) {
                    catFile(hash);
                } else {
                    System.out.println("provide the -p flag");
                }
                break;

            case "write-tree" :
                Set<String> ignoreSet = getIgnoreSet();
                Path root = Paths.get("");
                String treeHash = writeTree(ignoreSet, root);
                System.out.println(treeHash); // Output the final hash exactly like real Git
                break;

            case "commit-tree" :

                if(args.length < 4) {
                    System.out.println("Usage: commit-tree <tree-hash> -m <message> [-p <parent-hash>]");
                    break;
                }


                String treeHash1 = args[1];
                String message = null;
                String parentHash = null;

                for(int i = 1; i < args.length; i++) {
                    if(args[i].equals("-m") && i+1 < args.length) {
                        message = args[i+1];
                        i++;
                    } else if(args[i].equals("-p") && i+1 < args.length) {
                        parentHash = args[i+1];
                        i++;
                    }
                }

                if(message == null) {
                    System.out.println("Error: commit message required. Use -m <commit-message>");
                    break;
                }

                System.out.println(commitTree(treeHash1, message, parentHash));

                break;

            default:
                System.out.println(command + " is not a valid command");
                break;
        }
    }

    /**
     * Replicates `git init`.
     * Creates the hidden directory structure and the HEAD reference file.
     */
    public static void initializeRepository() {
        Path path = Paths.get(".minigit");
        List<String> subDirectoriesPath = List.of(
                ".minigit/objects",
                ".minigit/refs"
        );
        Path headPath = Paths.get(".minigit/HEAD.txt");
        String headContent = "ref: refs/HEAD/main\n";

        try {
            Files.createDirectory(path);

            for(String p : subDirectoriesPath) {
                Path subPath = Paths.get(p);
                Files.createDirectory(subPath);
            }

            Files.writeString(headPath, headContent);
            System.out.println("Initialized project");

        } catch (IOException e) {
            System.out.println("Failed to initialize project\n" + e.getMessage());
        }
    }

    public static void writeToDisk(String filename) {
        byte[] blob = createBlob(filename);
        String hexString = generateHexString(blob);
        saveGitObjectToDisk(hexString, blob);
    }

    /**
     * Reads a file and prepends the Git blob header.
     * Format: "blob <size in bytes>\0<file contents>"
     */
    public static byte[] createBlob(String filename) {
        Path filePath = Paths.get(filename);
        byte[] blob = null;
        try {
            byte[] byteFile = Files.readAllBytes(filePath);
            String filesize = String.valueOf(Files.size(filePath));

            // The null byte '\0' is critical: it separates the header from the actual data
            String header = "blob " + filesize + '\0';
            byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);

            blob = new byte[headerBytes.length + byteFile.length];

            System.arraycopy(headerBytes, 0, blob, 0, headerBytes.length);
            System.arraycopy(byteFile, 0, blob, headerBytes.length, byteFile.length);
        } catch (IOException e) {
            System.out.println("unable to read file " + e.getMessage());
        }
        return blob;
    }

    /**
     * Generates the 40-character SHA-1 hash for any byte array.
     */
    public static String generateHexString(byte[] blob) {
        if(blob == null) {
            throw new NullPointerException("blob is null. error creating blob");
        }
        String hexString = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashedBlob = digest.digest(blob);
            hexString = HexFormat.of().formatHex(hashedBlob);
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e.getMessage());
        }
        return hexString;
    }

    /**
     * Compresses (using zlib) and saves an object to the .minigit/objects directory.
     * Git splits the 40-char hash: first 2 chars = folder name, remaining 38 = file name.
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

    /**
     * Replicates `git cat-file -p`.
     * Decompresses an object, strips the Git header, and prints the raw contents.
     */
    public static void catFile(String hash) {
        String dirname  = hash.substring(0,2);
        String filename = hash.substring(2);
        Path objectPath = Paths.get(".minigit", "objects", dirname, filename);

        try(
                FileInputStream fis = new FileInputStream(objectPath.toFile());
                InflaterInputStream iis = new InflaterInputStream(fis); // Decompress zlib on the fly
        ) {
            byte[] data = iis.readAllBytes();
            int i = 0;

            // Iterate through the bytes until we find the null byte (0) that ends the header
            while(data[i] != 0) {
                i++;
            }

            // Slice the array to keep only the bytes AFTER the null byte
            data = Arrays.copyOfRange(data, i+1, data.length);
            String res = new String(data);
            System.out.print(res);

        } catch (IOException e) {
            System.out.println("error while reading file " + e.getMessage());
        }
    }

    /**
     * Replicates `git write-tree`.
     * Recursively traverses the working directory to build Tree objects representing folders.
     */
    public static String writeTree(Set<String> ignoreSet, Path path) {
        List<byte[]> treeEntries = new ArrayList<>();

        try (Stream<Path> stream = Files.list(path)) {
            // Git STRICTLY requires tree entries to be sorted alphabetically by filename
            List<Path> paths = stream.sorted().toList();

            for(Path p : paths) {
                String filename = p.getFileName().toString();

                // Prevent infinite loops by ignoring the .minigit/.git databases
                if(!ignoreSet.contains(filename) && !filename.equals(".minigit") && !filename.equals(".git") ) {

                    if(Files.isRegularFile(p)) {
                        treeEntries.add(getFileHexBytes(p));
                    } else if(Files.isDirectory(p)) {
                        // Recursively hash the sub-directory first
                        String subDirHash = writeTree(ignoreSet, p);
                        if(subDirHash != null) {
                            treeEntries.add(getDirectoryHexBytes(p, subDirHash));
                        }
                    } else {
                        System.out.println(p + " is something else");
                    }
                }
            }

            byte[] combinedTreeEntries = combineTreeEntries(treeEntries);

            // Just like blobs, trees need a header before hashing
            String treeHeader = "tree " + combinedTreeEntries.length + "\0";

            byte[] finalTreeObject;
            try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                baos.write(treeHeader.getBytes(StandardCharsets.UTF_8));
                baos.write(combinedTreeEntries);
                finalTreeObject = baos.toByteArray();
            }

            String treeHex = generateHexString(finalTreeObject);
            saveGitObjectToDisk(treeHex, finalTreeObject);

            return treeHex; // Return hash to parent directory for recursive building
        } catch (IOException e) {
            System.out.println("Error occurred while writing tree: " + e.getMessage());
        }
        return null;
    }

    /**
     * Formats a single file entry for a Git Tree.
     * Format: "[mode] [filename]\0[20-byte binary hash]"
     */
    public static byte[] getFileHexBytes(Path p) {
        String mode = "100644 "; // Standard file mode
        String filename = p.getFileName().toString();
        String header = mode + filename + '\0';

        byte[] blob = createBlob(p.toString());
        String blobHash = generateHexString(blob);
        saveGitObjectToDisk(blobHash, blob);

        // Git trees store the hash as 20 raw bytes, NOT a 40-character text string
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
     * Formats a single directory entry for a Git Tree.
     */
    public static byte[] getDirectoryHexBytes(Path p, String subDirHash) {
        String mode = "40000 "; // Directory mode
        String header = mode + p.getFileName().toString() + '\0';

        // Convert the child directory's 40-char string hash back into 20 binary bytes
        byte[] subDirHashBytes = HexFormat.of().parseHex(subDirHash);

        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            baos.write(header.getBytes(StandardCharsets.UTF_8));
            baos.write(subDirHashBytes);
            return  baos.toByteArray();
        } catch (IOException e) {
            System.out.println("Error getting hex byte for " + p.toString() + " directory. " + e.getMessage());
        }
        return null;
    }

    /**
     * Safely combines a list of byte arrays into a single continuous byte stream.
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
     * Reads a custom .mini-gitignore file to avoid tracking unneeded files/folders.
     */
    public static Set<String> getIgnoreSet() {
        Set<String> set = new HashSet<>();
        Path ignoreFile = Paths.get(".mini-gitignore");

        if(!Files.exists(ignoreFile)) return set;

        try( Stream<String> dirAndFilesToIgnoreStream = Files.lines(ignoreFile);) {
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

    public static String commitTree(String treeHash, String message, String parentHash) {
        String commitText = getCommitText(treeHash, message, parentHash);
        byte[] commitTextBytes = commitText.getBytes(StandardCharsets.UTF_8);

        String header = "commit " + commitTextBytes.length + "\0";

        byte[] combinedCommitBytes = null;
        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            baos.write(header.getBytes(StandardCharsets.UTF_8));
            baos.write(commitTextBytes);

            combinedCommitBytes = baos.toByteArray();
        } catch (IOException e) {
            System.out.println("Error creating header for commit. " + e.getMessage());
        }


        String commitHex = generateHexString(combinedCommitBytes);
        saveGitObjectToDisk(commitHex, combinedCommitBytes);

        return commitHex;
    }

    public static String getCommitText(String treeHash, String message, String parentHash) {
        long currentTime = System.currentTimeMillis() / 1000;

        StringBuilder sb = new StringBuilder();

        sb.append("tree ").append(treeHash).append("\n");
        if(parentHash != null) {
            sb.append("parent ").append(parentHash).append("\n");
        }
        sb.append("author User ").append("<user@example.com> ").append(currentTime).append(" +0000\n");
        sb.append("committer User ").append("<user@example.com ").append(currentTime).append(" +0000\n");
        sb.append("\n");
        sb.append(message).append("\n");

        return sb.toString();
    }
}