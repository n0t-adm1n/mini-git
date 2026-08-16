package commands;

import core.GitObject;

public class CommitTreeCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        if(args.length < 4) {
            System.out.println("Usage: commit-tree <tree-hash> -m <message> [-p <parent-hash>]");
            return;
        }

        String treeHash = args[1];
        String message = null;
        String parentHash = null;

        // Dynamically parse for message and parent flags regardless of order
        for(int i = 2; i < args.length; i++) {
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
            return;
        }

        System.out.println(GitObject.commitTree(treeHash, message, parentHash));
    }


}
