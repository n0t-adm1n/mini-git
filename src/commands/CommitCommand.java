package commands;

import core.GitObject;
import core.Repository;
import utils.FileUtils;

import java.nio.file.Paths;

public class CommitCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        if(args.length < 3) {
            System.out.println("Usage: commit -m \"<commit-message>\"");
            return;
        }

        String commitMessage = args[2];

        // snapshot of the directory
        String currentTreeHash = GitObject.writeTree(FileUtils.getIgnoreSet(), Paths.get(""));

        // get parents hash stored in HEAD file
        String parentCommitHash = Repository.getCurrentHeadHash();

        // create the commit object and get its hash
        String newCommitHash = GitObject.commitTree(currentTreeHash, commitMessage, parentCommitHash);

        // update branch pointers
        Repository.updateRef(newCommitHash);

        System.out.println("commit created. " + newCommitHash);

    }
}
