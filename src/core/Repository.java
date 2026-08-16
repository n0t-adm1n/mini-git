package core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Repository {
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

        Path ignorePath = Paths.get(".mini-gitignore");
        String defaultIgnoreContent = ".minigit\n.git\n.idea\nout\ntarget\n";

        try {

            if(Files.exists(path)) {
                System.out.println("Repository is already initialized.");
                return;
            }

            Files.createDirectory(path);

            for(String p : subDirectoriesPath) {
                Path subPath = Paths.get(p);
                Files.createDirectory(subPath);
            }

            Files.writeString(headPath, headContent);

            // create the .mini-gitignore file if it doesn't exists already
            if(!Files.exists(ignorePath)) {
                Files.writeString(ignorePath, defaultIgnoreContent);
            }


            System.out.println("Initialized project");

        } catch (IOException e) {
            System.out.println("Failed to initialize project\n" + e.getMessage());
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
            } else {
                // HEAD file doesn't exist we are in detached state
                headHash = ref;
            }
        } catch (IOException e) {
            System.out.println("error reading head ref. " + e.getMessage());
        }

        return headHash;
    }

    public static void updateRef(String commitHash) {
        Path path = Paths.get(".minigit/HEAD");

        try {
            if(!Files.exists(path)) return;

            String ref = Files.readString(path).trim();

            if (ref.startsWith("ref: ")) {
                // We are on a branch! Extract the branch path and update it.
                String branchPathStr = ref.substring(5);
                Path branchPath = Paths.get(".minigit", branchPathStr);

                Files.createDirectories(branchPath.getParent());
                Files.writeString(branchPath, commitHash + "\n");
            } else {
                // We are in a detached HEAD state! Update the HEAD file directly.
                Files.writeString(path, commitHash + "\n");
            }
        } catch (IOException e) {
            System.out.println("Error updating ref. " + e.getMessage());
        }
    }
}
