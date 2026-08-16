package commands;

import core.GitObject;

public class CheckoutCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        if(args.length < 2) {
            System.out.println("Usage: checkout <commit-hash>");
            return;
        }

        String commitHash = args[1];

        GitObject.checkout(commitHash);
    }
}
