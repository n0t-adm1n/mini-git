package commands;

import core.Repository;

public class UpdateRefCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        if(args.length < 2) {
            System.out.println("Usage: update-ref <commit-hash>");
            return;
        }

        String commitHashToSave = args[1];

        Repository.updateRef(commitHashToSave);
    }
}
