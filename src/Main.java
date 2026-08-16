import commands.*;
import java.io.*;
import java.util.*;


public class Main {
    public static void main(String[] args) {
        if(args.length == 0) {
            System.out.println("Usage: java Main <command> [<args>]");
            return;
        }

        String command = args[0];

        // Basic CLI Router to handle different Git commands
        switch (command) {
            case "init" :
                GitCommand initCmd = new InitCommand();
                initCmd.execute(args);
                break;

            case "hash-object" :
                GitCommand hashObjectCmd = new HashObjectCommand();
                hashObjectCmd.execute(args);
                break;

            case "cat-file" :
                GitCommand catFileCmd = new CatFileCommand();
                catFileCmd.execute(args);
                break;

            case "write-tree" :
                GitCommand writeTreeCmd = new WriteTreeCommand();
                writeTreeCmd.execute(args);
                break;

            case "commit-tree" :
                GitCommand commitTreeCmd = new CommitTreeCommand();
                commitTreeCmd.execute(args);
                break;

            case "log" :
                GitCommand logCmd = new LogCommand();
                logCmd.execute(args);
                break;

            case "update-ref" :
                GitCommand updateRefCmd = new UpdateRefCommand();
                updateRefCmd.execute(args);
                break;

            case "commit" :
                GitCommand commitCmd = new CommitCommand();
                commitCmd.execute(args);
                break;

            case "checkout" :
                GitCommand checkoutCmd = new CheckoutCommand();
                checkoutCmd.execute(args);
                break;


            default:
                System.out.println(command + " is not a valid command");
                break;
        }

    }
}