package commands;

import core.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class BranchCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        // no arguments passed with the command
        if(args.length == 1) {
            listBranches();
            return;
        }


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


    private void listBranches() {
        Path headsPath = Paths.get(".minigit/refs/heads");

        if(!Files.exists(headsPath)) return;

        // finding the active branch
        String activeBranch = null;

        try {
            Path headPath = Paths.get(".minigit/HEAD");
            if(Files.exists(headPath)) {
                String headContent = Files.readString(headPath).trim();
                if(headContent.contains(("ref: refs/heads/"))) {
                    activeBranch = headContent.substring(16);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading the HEAD file. " + e.getMessage());
        }

        // getting every branch name
        try(Stream<Path> paths = Files.list(headsPath)) {
            List<String> branches = paths
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();

            for(String branch : branches) {
                if(branch.equals(activeBranch)) {
                    System.out.println("* " + branch);  // highlighting the active branch
                } else {
                    System.out.println("  " + branch);
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading branch names. " + e.getMessage());
        }

    }
}
