package commands;

import core.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BranchCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        if(args.length != 2) {
            System.out.println("Usage: branch <branch-name>");
            return;
        }

        String branchName = args[1];

        String headHash = Repository.getCurrentHeadHash();

        if(headHash == null) {
            System.out.println("fatal error. no branch with name main");
            return;
        }

        Path path = Paths.get(".minigit/refs/heads", branchName);

        if(Files.exists(path)) {
            System.out.println("fatal error. branch with name " + branchName + " already exists.");
            return;
        }

        try {
            Files.writeString(path, headHash + "\n");
        } catch (IOException e) {
            System.out.println("error in creating branch " + branchName + ". " + e.getMessage());
        }
    }
}
