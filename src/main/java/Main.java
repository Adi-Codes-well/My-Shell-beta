import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage

         Scanner scanner = new Scanner(System.in);
         while (true){
             System.out.print("$ ");
             String command = scanner.nextLine();
             if (command.equals("exit 0")) break;
             else if (command.startsWith("echo")) {
                 System.out.println(command.substring(5));
             }
             else {
             System.out.println(command + ": command not found");
             }

         }

    }
}
