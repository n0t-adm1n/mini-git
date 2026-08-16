package commands;

public interface GitCommand {
    /**
     * Executes the Git command.
     * @param args The raw arguments passed from the terminal (e.g., ["init"] or ["hash-object", "-w", "file.txt"])
     */
    void execute(String[] args);
}
