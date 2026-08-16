package commands;

import core.GitObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CheckoutCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        if(args.length < 2) {
            System.out.println("Usage: checkout <commit-hash>");
            return;
        }

        String target = args[1];
        Path branchPath = Paths.get(".minigit/refs/heads", target);
        String targetHash = target;
        boolean isBranch = false;

        if(Files.exists(branchPath)) {
        // if the feature_branch is a branch
            isBranch = true;
            try {
                targetHash = Files.readString(branchPath).trim();
            } catch (IOException e) {
                System.out.println("error reading from branch " + target + ". " + e.getMessage());
            }
        }


        boolean success = GitObject.checkout(targetHash);

        if(!success) return;

        try{
            Path headPath = Paths.get(".minigit/HEAD");
            if(isBranch) {
                // attach head to branch
                Files.writeString(headPath, "ref: refs/heads/" + target + "\n");
                System.out.println("switch to " + target + " branch");
            } else {
                // Detached HEAD state
                Files.writeString(headPath, targetHash + "\n");
                System.out.println("Note: checking out '" + targetHash + "'.\nYou are in 'detached HEAD' state.");
            }
        } catch (IOException e) {
            System.out.println("Error updating HEAD: " + e.getMessage());
        }
    }
}
