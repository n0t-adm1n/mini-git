package commands;

import core.GitObject;
import utils.FileUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class WriteTreeCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        Set<String> ignoreSet = FileUtils.getIgnoreSet();
        Path root = Paths.get("");
        String treeHash = GitObject.writeTree(ignoreSet, root);
        System.out.println(treeHash); // Output the final hash exactly like real Git
    }
}
