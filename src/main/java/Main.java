import java.util.*;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();
            String[] commands = input.split(" ");

            switch (commands[0]) {
                case "exit":
                    System.exit(Integer.parseInt(commands[1]));
                    break;
                case "echo":
                    echo(commands);
                    break;
                case "type":
                    type(commands);
                    break;

                case "pwd":
                    pwd();
                    break;
                default:
                    runExternalCommand(commands);
                    break;
            }
        }
    }

    static void echo(String[] input) {
        boolean counter = false;
        for (int i = 1; i < input.length; i++) {
            if (counter) {
                System.out.print(" ");
            }
            System.out.print(input[i]);
            counter = true;
        }
        System.out.println();
    }

    static void type(String[] input) {
        String[] validCommands = {"exit", "type", "echo", "pwd"};
        if (search(validCommands, input[1])) {
            System.out.println(input[1] + " is a shell builtin");
            return;
        }
        String path = System.getenv("PATH");
        String[] dirs = path.split(":");

        for (String dir : dirs) {
            File file = new File(dir, input[1]);
            if (file.exists() && file.canExecute()) {
                System.out.println(input[1] + " is " + file.getAbsolutePath());
                return;
            }
        }
        System.out.println(input[1] + ": not found");

    }

    static boolean search(String[] input, String command) {
        for (String s : input) {
            if (s.equals(command)) {
                return true;
            }
        }
        return false;
    }

    static void printCNF(String input) {
        System.out.println(input + ": command not found");
    }

    static void runExternalCommand(String[] commands) {
        String cmd = commands[0];
        String path = System.getenv("PATH");
        String[] dirs = path.split(":");

        for (String dir : dirs) {
            File file = new File(dir, cmd);
            if (file.exists() && file.canExecute()) {

                try {
                    ProcessBuilder pb = new ProcessBuilder(commands);
                    pb.inheritIO(); // use terminal input/output
                    Process p = pb.start();
                    p.waitFor();
                } catch (Exception e) {
                    System.out.println("Error running command");
                }
                return;
            }

        }
        System.out.println(cmd + ": command not found");

    }

    static void pwd() {
        String currentDir = System.getProperty("user.dir");
        System.out.println(currentDir);
    }
}