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

        switch (command) {
            case "init" :
                initializeRepository();
                break;


            case "hash-object" :

                boolean writeFlag = false;
                String filename = "";

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
                    // print the content of the file on console
                    catFile(hash);
                } else {
                    System.out.println("provide the -p flag");
                }

                break;

            case "write-tree" :
                Set<String> ignoreSet = getIgnoreSet();
                Path root = Paths.get("");
                String treeHash = writeTree(ignoreSet, root);
                System.out.println(treeHash);

                break;

            default:
                System.out.println(command + " is not a valid command");
                break;
        }
    }

    public static void initializeRepository() {
        Path path = Paths.get(".minigit");
        List<String> subDirectoriesPath = List.of(
            ".minigit/objects",
            ".minigit/refs"
        );
        Path headPath = Paths.get(".minigit/HEAD.txt");
        String headContent = "ref: refs/HEAD/main\n";

        try {
            // create .minigit dir
            Files.createDirectory(path);

            // create objects and refs dirs
            for(String p : subDirectoriesPath) {
                Path subPath = Paths.get(p);
                Files.createDirectory(subPath);
            }

            // create HEAD file and write to it
            Files.writeString(headPath, headContent);

            System.out.println("Initialized project");

        } catch (IOException e) {
            System.out.println("Failed to initialize project");
            System.out.println(e.getMessage());
        }
    }

    public static void writeToDisk(String filename) {
        byte[] blob = createBlob(filename);
        String hexString = generateHexString(blob);

        saveGitObjectToDisk(hexString, blob);
    }

    public static byte[] createBlob(String filename) {
        Path filePath = Paths.get(filename);

        byte[] blob = null;
        try {
            byte[] byteFile = Files.readAllBytes(filePath);

            String filesize = String.valueOf(Files.size(filePath));
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

    public static String generateHexString(byte[] blob) {

        if(blob == null) {
            throw new NullPointerException("blob is null. error creating blob");
        }

        String hexString = null;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hashedBlob = digest.digest(blob);

            hexString = HexFormat.of().formatHex(hashedBlob);
            //System.out.println(hexString);
        } catch (NoSuchAlgorithmException e) {
            System.out.println(e.getMessage());
        }

        return hexString;
    }

    public static void saveGitObjectToDisk(String hexString, byte[] rawData) {
        String folderName = hexString.substring(0, 2);
        String fileName   = hexString.substring(2, 40);

        Path objectPath = Paths.get(".minigit", "objects", folderName, fileName);

        try {
            Files.createDirectories(objectPath.getParent());

            try(FileOutputStream fos = new FileOutputStream(objectPath.toFile());
                DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {

                dos.write(rawData);

            }

        } catch (IOException e) {
            System.out.println("Error saving to disk. " + e.getMessage());
        }
    }

    public static void catFile(String hash) {
        String dirname  = hash.substring(0,2);
        String filename = hash.substring(2);

        Path objectPath = Paths.get(".minigit", "objects", dirname, filename);

        try(
                FileInputStream fis = new FileInputStream(objectPath.toFile());
                InflaterInputStream iis = new InflaterInputStream(fis);
        ) {

            byte[] data = iis.readAllBytes();
            int i = 0;

            // skip till the null char in header of blob
            while(data[i] != 0) {
                i++;
            }
            // currently i has the null byte index

            // only keep the byte after the header and the null char
            data = Arrays.copyOfRange(data, i+1, data.length);
            String res = new String(data);

            // print the content of the file to console
            System.out.print(res);

        } catch (IOException e) {
            System.out.println("error while reading file " + e.getMessage());
        }
    }

    public static String writeTree(Set<String> ignoreSet, Path path ) {

        List<byte[]> treeEntries = new ArrayList<>();

        try (
                Stream<Path> stream = Files.list(path);
        ){

            List<Path> paths = stream.sorted().toList();


            for(Path p : paths) {
                String filename = p.getFileName().toString();
                if(!ignoreSet.contains(filename) && !filename.equals(".minigit") && !filename.equals(".git") ) {
                    // handle if a path points to a file or dir
                    if(Files.isRegularFile(p)) {



                        String mode = "100644 ";

                        String header = mode + filename + '\0';

                        byte[] blob = createBlob(p.toString());
                        String blobHash = generateHexString(blob);

                        saveGitObjectToDisk(blobHash, blob);

                        byte[] fileHashByte = HexFormat.of().parseHex(blobHash);

                        try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            baos.write(header.getBytes());
                            baos.write(fileHashByte);

                            treeEntries.add(baos.toByteArray());
                        } catch(IOException e) {
                            System.out.println("Error combining header with blob. " + e.getMessage());
                        }

                    } else if(Files.isDirectory(p)) {

                        String subDirHash = writeTree(ignoreSet, p);

                        if(subDirHash != null) {

                            String mode = "40000 ";
                            String header = mode + filename + '\0';

                            byte[] subDirHashBytes = HexFormat.of().parseHex(subDirHash);
                            try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                                baos.write(header.getBytes(StandardCharsets.UTF_8));
                                baos.write(subDirHashBytes);

                                treeEntries.add(baos.toByteArray());
                            }

                        }

                    } else {

                        System.out.println(p + " is something else");

                    }
                }
            }

            // combining all the treeEntries
            byte[] combinedTreeEntries;
            try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                for(byte[] b : treeEntries) {
                    baos.write(b);
                }

                combinedTreeEntries = baos.toByteArray();
            }

            // create tree header and final object containing all the files
            String treeHeader = "tree " + combinedTreeEntries.length + "\0";

            byte[] finalTreeObject;
            try(ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                baos.write(treeHeader.getBytes(StandardCharsets.UTF_8));
                baos.write(combinedTreeEntries);

                finalTreeObject = baos.toByteArray();
            }

            String treeHex = generateHexString(finalTreeObject);
            saveGitObjectToDisk(treeHex, finalTreeObject);

            return treeHex;
        } catch (IOException e) {
            System.out.println("Error occurred while writing tree: " + e.getMessage());
        }


        return null;
    }

    public static Set<String> getIgnoreSet() {
        Set<String> set = new HashSet<>();

        Path ignoreFile = Paths.get(".mini-gitignore");

        try( Stream<String> dirAndFilesToIgnoreStream = Files.lines(ignoreFile);) {
            for(String s : dirAndFilesToIgnoreStream.toList()) {
                String st = s.trim();

                if(st.isEmpty()) continue;

                // remove the / from the dir names
                if(st.endsWith("/")) st = st.substring(0, st.length()-1);

                set.add(st);
            }
        } catch (IOException e) {
            System.out.println("cannot read .mini-gitignore file " + e.getMessage());
        }

        return set;
    }
}
