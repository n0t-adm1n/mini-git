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
    public static void main(String[] args) {
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
                    System.out.println(catFile(hash));
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

                // Dynamically parse for message and parent flags regardless of order
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

            case "log" :
                String headHash = getCurrentHeadHash();

                if(headHash != null) {
                    log(headHash);
                } else {
                    System.out.println("No commit found!");
                }
                break;


            case "update-ref" :

                if(args.length < 2) {
                    System.out.println("Usage: update-ref <commit-hash>");
                    break;
                }

                String commitHashToSave = args[1];

                updateRef(commitHashToSave);
                break;


            case "commit" :
                if(args.length < 3) {
                    System.out.println("Usage: commit -m \"<commit-message>\"");
                    break;
                }

                String commitMessage = args[2];

                // snapshot of the directory
                String currentTreeHash = writeTree(getIgnoreSet(), Paths.get(""));

                // get parents hash stored in HEAD file
                String parentCommitHash = getCurrentHeadHash();

                // create the commit object and get its hash
                String newCommitHash = commitTree(currentTreeHash, commitMessage, parentCommitHash);

                // update branch pointers
                updateRef(newCommitHash);

                System.out.println("commit created. " + newCommitHash);

                break;

            case "checkout" :

                if(args.length < 2) {
                    System.out.println("Usage: checkout <commit-hash>");
                    break;
                }

                String commitHash = args[1];

                checkout(commitHash);

                break;


            default:
                System.out.println(command + " is not a valid command");
                break;
        }
    }

    /**
     * Replicates `git init`.
     * Creates the hidden directory structure required for a Git database.
     * Also initializes the HEAD file which acts as a pointer to the current active branch.
     */
    public static void initializeRepository() {
        Path path = Paths.get(".minigit");
        List<String> subDirectoriesPath = List.of(
                ".minigit/objects",
                ".minigit/refs"
        );
        Path headPath = Paths.get(".minigit/HEAD");
        String headContent = "ref: refs/heads/main\n";

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

    /**
     * High-level wrapper to create a blob, hash it, and save it to the disk.
     */
    public static void writeToDisk(String filename) {
        byte[] blob = createBlob(filename);
        String hexString = generateHexString(blob);
        saveGitObjectToDisk(hexString, blob);
    }

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
     * Generates a 40-character SHA-1 hash (checksum) for any given byte array.
     * This hash acts as the unique ID and filename for the object in Git's key-value datastore.
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

    /**
     * Replicates `git cat-file -p`.
     * Locates a compressed object by its hash, decompresses it on the fly,
     * strips away the Git header, and returns the raw file contents as a String.
     */
    public static String catFile(String hash) {
        String dirname  = hash.substring(0,2);
        String filename = hash.substring(2);
        Path objectPath = Paths.get(".minigit", "objects", dirname, filename);

        try(
                FileInputStream fis = new FileInputStream(objectPath.toFile());
                InflaterInputStream iis = new InflaterInputStream(fis); // Decompresses zlib data
        ) {
            byte[] data = iis.readAllBytes();
            int i = 0;

            // Iterate through the bytes until we find the null byte (0) that ends the header
            while(data[i] != 0) {
                i++;
            }

            // Slice the array to keep only the actual payload (everything AFTER the null byte)
            data = Arrays.copyOfRange(data, i+1, data.length);
            return new String(data);

        } catch (IOException e) {
            System.out.println("error while reading file " + e.getMessage());
        }

        return null;
    }


    /**
     * Helper method for checkout and write-tree.
     * Decompresses a Git object and returns its RAW bytes, preserving binary hashes.
     */
    public static byte[] getRawObjectBytes(String hash) {
        String dirname  = hash.substring(0, 2);
        String filename = hash.substring(2);
        Path objectPath = Paths.get(".minigit", "objects", dirname, filename);

        try (
                FileInputStream fis = new FileInputStream(objectPath.toFile());
                InflaterInputStream iis = new InflaterInputStream(fis)
        ) {
            // Return the entire decompressed file (including the header!) as raw bytes
            return iis.readAllBytes();

        } catch (IOException e) {
            System.out.println("error while reading raw object bytes: " + e.getMessage());
        }
        return null;
    }

    /**
     * Replicates `git write-tree`.
     * Recursively traverses the working directory to build Tree objects.
     * A Tree object in Git acts like a directory, mapping filenames to Blob hashes or other Tree hashes.
     */
    public static String writeTree(Set<String> ignoreSet, Path path) {
        List<byte[]> treeEntries = new ArrayList<>();

        try (Stream<Path> stream = Files.list(path)) {
            // Git STRICTLY requires tree entries to be sorted alphabetically by filename
            List<Path> paths = stream.sorted().toList();

            for(Path p : paths) {
                String filename = p.getFileName().toString();

                // Prevent infinite loops by ignoring the .minigit/.git databases and the ignore list
                if(!ignoreSet.contains(filename) && !filename.equals(".minigit") && !filename.equals(".git") ) {

                    if(Files.isRegularFile(p)) {
                        treeEntries.add(getFileHexBytes(p));
                    } else if(Files.isDirectory(p)) {
                        // Recursively process subdirectories
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

            // Trees require a header before hashing, just like Blobs
            String treeHeader = "tree " + combinedTreeEntries.length + "\0";

            byte[] finalTreeObject;
            try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                baos.write(treeHeader.getBytes(StandardCharsets.UTF_8));
                baos.write(combinedTreeEntries);
                finalTreeObject = baos.toByteArray();
            }

            String treeHex = generateHexString(finalTreeObject);
            saveGitObjectToDisk(treeHex, finalTreeObject);

            return treeHex; // Return hash to the parent directory for recursive building
        } catch (IOException e) {
            System.out.println("Error occurred while writing tree: " + e.getMessage());
        }
        return null;
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
        String blobHash = generateHexString(blob);
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
     * Formats a single directory entry to be inserted into a parent Tree object.
     */
    public static byte[] getDirectoryHexBytes(Path p, String subDirHash) {
        String mode = "40000 "; // Standard directory mode in Git
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
     * Reads a custom `.mini-gitignore` file to avoid tracking unneeded files/folders.
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

    /**
     * Replicates `git commit-tree`.
     * Wraps a Tree hash into a Commit object, linking it to a parent commit to form the repository history.
     */
    public static String commitTree(String treeHash, String message, String parentHash) {
        String commitText = getCommitText(treeHash, message, parentHash);
        byte[] commitTextBytes = commitText.getBytes(StandardCharsets.UTF_8);

        // Commits also require a header before hashing
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
     * Replicates `git log`.
     * Recursively reads commits starting from a given hash and traversing backwards through parents.
     */
    public static void log(String commitHash) {
        String commitStr = catFile(commitHash);

        String[] strs = commitStr.split("\n");
        boolean isMessage = false;
        String parentHash = null;
        StringBuilder commitMessage = new StringBuilder();

        // Parse the commit payload line by line
        for(String line : strs) {
            if(isMessage) {
                commitMessage.append(line).append("\n");
            } else if(line.startsWith("parent ")) {
                parentHash = line.substring(7); // Extract the 40-char hash next to the "parent " string
            } else if(line.isEmpty()) {
                isMessage = true;  // The first empty line separates headers from the commit message
            }
        }

        System.out.println("commit " + commitHash);
        System.out.println("\n    " + commitMessage.toString().trim() + "\n");

        // Follow the DAG backwards recursively
        if(parentHash != null) {
            log(parentHash);
        }
    }

    public static void updateRef(String commitHash) {
        Path path = Paths.get(".minigit/refs/heads/main");

        try {
            Files.createDirectories(path.getParent()); // will create the missing directories

            Files.writeString(path, commitHash + "\n");
        } catch (IOException e) {
            System.out.println("Error updating main ref. " + e.getMessage());
        }
    }

    public static String getCurrentHeadHash() {
        String headHash = null;
        Path headPath = Paths.get(".minigit/HEAD");
        try {
            // if no head file exists
            if(!Files.exists(headPath)) return null;

            String ref = Files.readString(headPath).trim();  // remove /n

            if(ref.startsWith("ref: ")) {
                ref = ref.substring(5);

                Path branchPath = Paths.get(".minigit/" + ref);
                if(Files.exists(branchPath))
                    headHash = Files.readString(branchPath).trim(); // remove /n
            }
        } catch (IOException e) {
            System.out.println("error reading head ref. " + e.getMessage());
        }

        return headHash;
    }

    public static void checkout(String commitHash) {
        // get commit object
        String commitObject = catFile(commitHash);

        // read tree hash from commit object
        String[] strs = commitObject.split("\n");
        String treeHash = null;
        for(String s : strs) {
            if(s.startsWith("tree ")) {
                treeHash = s.substring(5);
                break;
            }
        }


        clearWorkingDirectory(Paths.get(""), getIgnoreSet());

        checkoutTree(treeHash, Paths.get(""));

        // update the HEAD to point to checked out commit and prevent the detached HEAD issue
        try {
            Files.writeString(Paths.get(".minigit/HEAD"), commitHash);
            System.out.println("HEAD is now at " + commitHash);
        } catch (IOException e) {
            System.out.println("Error updating HEAD. " + e.getMessage());
        }

    }

    public static void checkoutTree(String treeHash, Path basePath) {
        byte[] treeBytes = getRawObjectBytes(treeHash);

        if(treeBytes == null) {
            System.out.println("Error reading tree object.");
            return;
        }

        //finding where header ends (null byte)
        int i = 0;
        while(treeBytes[i] != 0) {
            i++;
        }
        i++;

        while(i < treeBytes.length) {

            //getting file mode
            int modeStart = i;
            while(treeBytes[i] != ' ') {
                i++;
            }
            String mode = new String(treeBytes, modeStart, i-modeStart, StandardCharsets.UTF_8);
            i++;

            //get the start and end of a file byte
            int nameStart = i;
            while(treeBytes[i] != 0) {
                i++;
            }

            String filename = new String(treeBytes, nameStart, i-nameStart, StandardCharsets.UTF_8);
            i++;

            // get the raw file hash
            byte[] rawHash = Arrays.copyOfRange(treeBytes, i , i+20);
            i += 20;

            String objectHash = HexFormat.of().formatHex(rawHash);

            Path targetPath = basePath.resolve(filename);

            // if mode is directory
            if(mode.equals("40000")) {
                try{
                    Files.createDirectories(targetPath);
                    System.out.println("Created directory: " + targetPath);

                    checkoutTree(objectHash, targetPath);
                } catch (IOException e) {
                    System.out.println("Error creating directory. " + e.getMessage());
                }
            } else {
                // if is is a file
                System.out.println("Restoring file: " + filename);
                // convert the filehash back to text form
                String fileContent = catFile(objectHash);

                try{
                    Files.writeString(targetPath, fileContent);
                } catch (IOException e) {
                    System.out.println("Error writing to " + filename + ". " + e.getMessage());
                }
            }



        }
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
}