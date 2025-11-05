import java.util.*;
import java.io.*;




public class Main {

// Global variable
static File currentDir = new File(System.getProperty("user.dir"));
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            String input = scanner.nextLine();
            List<String> parsed = parseCommand(input);

            String[] commands = parsed.toArray(new String[0]);

            boolean redirect = false;
            String outFile = null;

            if (parsed.size() >= 2 && parsed.get(parsed.size() - 2).equals("__REDIR__")) {
                redirect = true;
                outFile = parsed.get(parsed.size() - 1);

                // remove __REDIR__ and filename from command list
                parsed = parsed.subList(0, parsed.size() - 2);
                commands = parsed.toArray(new String[0]);
            }


            switch (commands[0]) {
                case "exit":
                    System.exit(Integer.parseInt(commands[1]));
                    break;
                case "echo":
                    if (redirect && outFile != null) {
                        // capture echo output into string
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i < commands.length; i++) {
                            if (i > 1) sb.append(" ");
                            sb.append(commands[i]);
                        }
                        sb.append("\n");

                        writeToFile(outFile, sb.toString());
                    } else {
                        echo(commands);
                    }
                    break;
                case "type":
                    type(commands);
                    break;

                case "pwd":
                    pwd();
                    break;

                case "cd":
                    cd(commands);
                    break;
                default:
                    runExternalCommand(commands, redirect, outFile);
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

    static void runExternalCommand(String[] commands, boolean redirect, String outFile) {
        String cmd = commands[0];
        String path = System.getenv("PATH");
        String[] dirs = path.split(":");

        boolean redirectErr = false;
        String errFile = null;

        List<String> cmdList = new ArrayList<>(Arrays.asList(commands));
        if (cmdList.size() >= 2 && cmdList.get(cmdList.size() - 2).equals("__REDIR_ERR__")) {
            redirectErr = true;
            errFile = cmdList.get(cmdList.size() - 1);
            cmdList = cmdList.subList(0, cmdList.size() - 2);
            commands = cmdList.toArray(new String[0]);
        }

        for (String dir : dirs) {
            File file = new File(dir, cmd);
            if (file.exists() && file.canExecute()) {

                try {
                    ProcessBuilder pb = new ProcessBuilder(commands);
                    pb.directory(currentDir);
                    if (redirect && outFile != null) {
                        pb.redirectOutput(new File(outFile));
                        //pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

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
        System.out.println(currentDir.getAbsolutePath());
    }

    static void cd(String[] commands) {
        if (commands.length < 2) {
            return;
        }

        String path = commands[1];

        File newDir;

        // Absolute path
        if (path.startsWith("/")) {
            newDir = new File(path);
        } else if (path.startsWith("~")) {
            String home = System.getenv("HOME");

            if (home == null) {
                home = System.getProperty("user.home");
            }

            newDir = new File(home);
        }
        // relative path
        else {
            newDir = new File(currentDir, path);
        }

        try {
            if (newDir.exists() && newDir.isDirectory()) {
                currentDir = newDir.getCanonicalFile(); // normalize path (removes ./ and ../)
                System.setProperty("user.dir", currentDir.getAbsolutePath());
            } else {
                System.out.println("cd: " + path + ": No such file or directory");
            }
        } catch (IOException e) {
            System.out.println("cd: " + path + ": No such file or directory");
        }

    }

    static List<String> parseCommand(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;

                // if we just closed quotes and buffer is empty, it's an empty token
                if (!inSingle && current.isEmpty()) {
                    result.add("");
                }
                continue;
            }

            if (c == '"' && !inSingle) {
                inDouble = !inDouble;

                // if we just closed quotes and buffer is empty, it's an empty token
                if (!inDouble && current.isEmpty()) {
                    result.add("");
                }
                continue;
            }

            // Backslash escapes
            if (c == '\\' && !inSingle) {

                if (inSingle) {
                    current.append('\\');
                    continue;
                }

                if (inDouble) {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '\\' || next == '"' || next == '$' || next == '`') {
                            current.append(next);
                            i++;
                            continue;
                        }
                    }
                    current.append('\\');
                    continue;
                }

                    if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);

                    // Handle newline escape (\n)
                    /*if (next == 'n') {
                        current.append('\n');
                        i++;
                        continue;
                    }*/

                    // Handle escaped space (\ )
                    if (next == ' ') {
                        current.append(' ');
                        i++;
                        continue;
                    }

                    // Otherwise literal next char
                    current.append(next);
                    i++;
                    continue;
                }

                // Trailing backslash
                current.append('\\');
                continue;
            }



            if (c == ' ' && !inSingle && !inDouble) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            }



            else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        List<String> cleaned = new ArrayList<>();
        String outFile = null;
        String errFile = null;

        for (int i = 0; i < result.size(); i++) {
            String token = result.get(i);

            if (token.equals(">") || token.equals("1>")) {
                outFile = result.get(i + 1);
                i++;
                continue;
            } else if (token.startsWith(">")) {
                outFile = token.substring(1);
                continue;
            } else if (token.startsWith("1>")) {
                outFile = token.substring(2);
                continue;
            }

            // stderr redirection
            if (token.equals("2>")) {
                errFile = result.get(i + 1);
                i++;
                continue;
            } else if (token.startsWith("2>")) {
                errFile = token.substring(2);
                continue;
            }
            cleaned.add(token);
        }

        if (outFile != null) {
            cleaned.add("__REDIR__");
            cleaned.add(outFile);
        }

        if (errFile != null) {
            cleaned.add("__REDIR_ERR__");
            cleaned.add(errFile);
        }

        return cleaned;
    }

    static void writeToFile(String file, String content) {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
        } catch (IOException e) {
            System.out.println("Error writing to file");
        }
    }

}