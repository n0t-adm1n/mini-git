package commands;

import core.GitObject;
import utils.FileUtils;
import utils.HashUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class StatusCommand implements GitCommand{
    @Override
    public void execute(String[] args) {
        Path headPath = Paths.get(".minigit/HEAD");
        if(!Files.exists(headPath)) {
            System.out.println("fatal error, cannot find HEAD");
            return;
        }

        // getting the path to current branch
        String currentBranchPath = null;
        try {
            String headContent = Files.readString(headPath).trim();

            if(headContent.contains("ref: ")) {
                currentBranchPath = headContent.substring(5);
            }
        } catch (IOException e) {
            System.out.println("Error reading HEAD file. " + e.getMessage());
        }

        if(currentBranchPath == null) return;

        // getting the tree hash of commit the current branch is pointing to
        String treeHash = null;
        try {
            String commitHash = Files.readString(Paths.get(".minigit", currentBranchPath)).trim();
            String commitContents =  GitObject.catFile(commitHash);

            String[] strs = commitContents.split("\n");


            for(String s : strs) {
                if(s.startsWith("tree ")) {
                    treeHash = s.substring(5);
                }
            }
        } catch (IOException e) {
            System.out.println("error reading commit at " + currentBranchPath + ". " + e.getMessage());
        }

        if(treeHash == null) {
            System.out.println("fatal error. tree hash is null");
            return;
        }


        Map<String, String> baselineMap = new HashMap<>();
        parseTreeToMap(treeHash, baselineMap, "");

        Set<String> ignoreSet = FileUtils.getIgnoreSet();
        Map<String, String> workingDirMap = new HashMap<>();
        parseWorkingDirToMap(workingDirMap, "", ignoreSet);

        compare(baselineMap, workingDirMap);
    }

    // traverse the tree of currently pointing commit and store file path and its hash in map
    private void parseTreeToMap(String treeHash, Map<String, String> map, String basePath) {
        byte[] treeBytes = GitObject.getRawObjectBytes(treeHash);

        int i = 0;

        while(i < treeBytes.length && treeBytes[i] != 0) {
            i++;
        }
        i++;

        while(i < treeBytes.length) {

            int modeStartIndex = i;
            while (treeBytes[i] != ' ') {
                i++;
            }
            String mode = new String(treeBytes, modeStartIndex, i-modeStartIndex, StandardCharsets.UTF_8);
            i++;

            // reading name
            int fileStartIndex = i;
            while(treeBytes[i] != 0) {
                i++;
            }
            String filename = new String(treeBytes, fileStartIndex, i-fileStartIndex, StandardCharsets.UTF_8);
            i++;


            // reading hash
            byte[] rawHash = Arrays.copyOfRange(treeBytes, i, i+20);
            i += 20;

            String objectHash = HexFormat.of().formatHex(rawHash);

            if(mode.equals("40000")) {
                parseTreeToMap(objectHash, map, basePath + filename + "/");
            } else {
                map.put(basePath + filename, objectHash);
            }
        }
    }

    // traverse the working directory and store the file path and its hash in map
    private void parseWorkingDirToMap(Map<String, String> map, String basePath, Set<String> ignoreSet) {
        Path bp = Paths.get(basePath);

        if(!Files.exists(bp)) {
            return;
        }

        try(Stream<Path> paths = Files.list(bp)) {
            List<String> pathList = paths
                    .map(p -> p.getFileName().toString())
                    .sorted().toList();

            for(String path : pathList) {
                if(ignoreSet.contains(path)) continue;

                if(Files.isDirectory(Paths.get(basePath + path))) {
                    parseWorkingDirToMap(map, basePath + path + "/", ignoreSet);
                } else {
                    byte[] blob = GitObject.createBlob(basePath + path);
                    String hexHash = HashUtils.generateHexString(blob);

                    map.put(basePath + path, hexHash);
                }
            }
        } catch (IOException e) {
            System.out.println("error reading working directory. " + e.getMessage());
        }
    }

    private void compare(Map<String, String> baselineMap, Map<String, String> workingDirMap) {
        List<String> modified  = new ArrayList<>();
        List<String> untracked = new ArrayList<>();
        List<String> deleted   = new ArrayList<>();

        for(String key : workingDirMap.keySet()) {
            if(!baselineMap.containsKey(key)) {
                untracked.add(key);
            } else if(!workingDirMap.get(key).equals(baselineMap.get(key))) {
                modified.add(key);
            }
        }

        for(String key : baselineMap.keySet()) {
            if(!workingDirMap.containsKey(key)) {
                deleted.add(key);
            }
        }

        if(modified.isEmpty() && untracked.isEmpty() && deleted.isEmpty()) {
            System.out.println("nothing to commit, working tree clean");
            return;
        }

        for(String s : untracked) {
            System.out.println("untracked:  " + s);
        }
        System.out.println();

        for(String s : modified) {
            System.out.println("modified:   " + s);
        }
        System.out.println();


        for(String s : deleted) {
            System.out.println("deleted:    " + s);
        }

    }
}
