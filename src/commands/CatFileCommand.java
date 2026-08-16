package commands;

import core.GitObject;

public class CatFileCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        boolean printFlag = false;
        String hash = "";

        // Parse arguments for the -p (print) flag and the object hash
        for(int i = 1; i < args.length; i++) {
            if(args[i].equals("-p")) {
                printFlag = true;
            } else {
                hash = args[i];
            }
        }

        if(hash.isEmpty()) {
            System.out.println("Please provide a hash to read");
            return;
        }

        if(printFlag) {
            System.out.println(GitObject.catFile(hash));
        } else {
            System.out.println("provide the -p flag");
        }
    }
}
