package commands;

import core.GitObject;
import core.Repository;

public class LogCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        String headHash = Repository.getCurrentHeadHash();

        if(headHash != null) {
            GitObject.log(headHash);
        } else {
            System.out.println("No commit found!");
        }
    }
}
