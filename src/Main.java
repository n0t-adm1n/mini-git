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
}
