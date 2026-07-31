import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    static void main(String[] args) {
        String command = args[0];

        switch (command) {
            case "init" :
                initializeRepository();
                break;
            default:
                System.out.println(command + "is not a valid command");
                break;
        }
    }

    public static void initializeRepository() {
        Path path = Paths.get(".minigit");
        List<String> subDirectoriesPath = List.of(
            ".minigit/objects",
            ".minigit/refs"
        );
        Path headPath = Paths.get(".minigit/HEAD.txt");
        String headContent = "ref: refs/HEAD/main\n";

        try {
            // create .minigit dir
            Files.createDirectory(path);

            // create objects and refs dirs
            for(String p : subDirectoriesPath) {
                Path subPath = Paths.get(p);
                Files.createDirectory(subPath);
            }

            // create HEAD file and write to it
            Files.writeString(headPath, headContent);

            System.out.println("Initialized project");

        } catch (IOException e) {
            System.out.println("Failed to initialize project");
            System.out.println(e.getMessage());
        }
    }
}
