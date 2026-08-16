package commands;

import core.GitObject;
import utils.HashUtils;

public class HashObjectCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        boolean writeFlag = false;
        String filename = "";

        // Parse arguments for the -w flag and filename
        for(int i = 1; i < args.length; i++) {
            if(args[i].equals("-w")) {
                writeFlag = true;
            } else {
                filename = args[i];
            }
        }

        if(filename.isEmpty()) {
            System.out.println("please enter a file name");
            return;
        }

        byte[] blob = GitObject.createBlob(filename);

        if(blob == null) {
            return;
        }

        try{
            String hexString = HashUtils.generateHexString(blob);

            if (writeFlag) {
                GitObject.saveGitObjectToDisk(hexString, blob);
            } else {
                System.out.println(hexString);
            }
        } catch (NullPointerException e) {
            System.out.println("Cannot generate hex string. " + e.getMessage());
        }
    }
}
