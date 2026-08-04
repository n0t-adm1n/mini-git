import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

public class Main {
    static void main(String[] args) {
        String command = args[0];
        String filename = "";
        if(args.length >= 2) {
            if(args[1].equals("-w")) {
                if(args.length > 2) filename = args[2];
            } else {
                filename = args[1];
            }
        }
        switch (command) {
            case "init" :
                initializeRepository();
                break;
            case "hash-object" :
                if(filename.isEmpty()) {
                    System.out.println("please enter a file name");
                    break;
                }

                writeToDisk(filename);
                break;
            default:
                System.out.println(command + "is not a valid command");
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

        String folderName = hexString.substring(0, 2);
        String fileName   = hexString.substring(2, 40);

        Path objectPath = Paths.get(".minigit", "objects", folderName, fileName);

        try {
            Files.createDirectories(objectPath.getParent());

            try(FileOutputStream fos = new FileOutputStream(objectPath.toFile());
                DeflaterOutputStream dos = new DeflaterOutputStream(fos)) {

                dos.write(blob);

            }
            System.out.println("Successfully wrote the object to" + objectPath);

        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public static byte[] createBlob(String filename) {
        Path filePath = Paths.get("src/" + filename);

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
}
