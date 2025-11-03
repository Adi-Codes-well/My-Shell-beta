import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        while (true) {
            System.out.print("$ ");
            Scanner scanner = new Scanner(System.in);
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
                default:
                    printCNF(commands[0]);
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
        String[] validCommands = {"exit", "type", "echo"};
        if (search(validCommands, input[1])) {
            System.out.println(input[1] + " is a shell builtin");
        } else {
            System.out.println(input[1] + ": not found");
        }
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
}