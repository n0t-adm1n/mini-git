package commands;

import core.Repository;

public class InitCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        if(args.length > 1) {
            System.out.println("Usage: init");
            return;
        }

        Repository.initializeRepository();
    }
}
