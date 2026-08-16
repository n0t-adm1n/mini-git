import commands.*;
import core.GitObject;
import core.Repository;
import utils.FileUtils;
import utils.HashUtils;

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
                GitCommand initCmd = new InitCommand();
                initCmd.execute(args);
                break;

            case "hash-object" :
                GitCommand hashObjectCmd = new HashObjectCommand();
                hashObjectCmd.execute(args);
                break;

            case "cat-file" :
                GitCommand catFileCmd = new CatFileCommand();
                catFileCmd.execute(args);
                break;

            case "write-tree" :
                GitCommand writeTreeCmd = new WriteTreeCommand();
                writeTreeCmd.execute(args);
                break;

            case "commit-tree" :
                GitCommand commitTreeCmd = new CommitTreeCommand();
                commitTreeCmd.execute(args);
                break;

            case "log" :
                GitCommand logCmd = new LogCommand();
                logCmd.execute(args);
                break;

            case "update-ref" :

                if(args.length < 2) {
                    System.out.println("Usage: update-ref <commit-hash>");
                    break;
                }

                String commitHashToSave = args[1];

                Repository.updateRef(commitHashToSave);
                break;


            case "commit" :
                if(args.length < 3) {
                    System.out.println("Usage: commit -m \"<commit-message>\"");
                    break;
                }

                String commitMessage = args[2];

                // snapshot of the directory
                String currentTreeHash = writeTree(FileUtils.getIgnoreSet(), Paths.get(""));

                // get parents hash stored in HEAD file
                String parentCommitHash = Repository.getCurrentHeadHash();

                // create the commit object and get its hash
                String newCommitHash = commitTree(currentTreeHash, commitMessage, parentCommitHash);

                // update branch pointers
                Repository.updateRef(newCommitHash);

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
     * Helper method for checkout and write-tree.
     * Decompresses a Git object and returns its RAW bytes, preserving binary hashes.
     */
    public static byte[] getRawObjectBytes(String hash) {
        String dirname  = hash.substring(0, 2);
        String filename = hash.substring(2);
        Path objectPath = Paths.get(".minigit", "objects", dirname, filename);

        try {
            return FileUtils.decompressZlibFile(objectPath);
        } catch (IOException e) {
            System.out.println("error while reading raw object bytes: " + e.getMessage());
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


        FileUtils.clearWorkingDirectory(Paths.get(""), FileUtils.getIgnoreSet());

        checkoutTree(treeHash, Paths.get(""));

        // update the HEAD to point to checked out commit and prevent the detached HEAD issue
        try {
            Files.writeString(Paths.get(".minigit/HEAD"), commitHash + "\n");
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
}