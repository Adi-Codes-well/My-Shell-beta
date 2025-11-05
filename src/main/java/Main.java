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

            String outFile = null;

            // detect append first
            boolean append = false;
            if (parsed.size() >= 2 && parsed.get(parsed.size() - 2).equals("__APPEND__")) {
                append = true;
                outFile = parsed.get(parsed.size() - 1);
                parsed = parsed.subList(0, parsed.size() - 2);
                commands = parsed.toArray(new String[0]);
            }

// detect overwrite only if not append
            boolean redirect = false;
            if (!append && parsed.size() >= 2 && parsed.get(parsed.size() - 2).equals("__REDIR__")) {
                redirect = true;
                outFile = parsed.get(parsed.size() - 1);
                parsed = parsed.subList(0, parsed.size() - 2);
                commands = parsed.toArray(new String[0]);
            }



            switch (commands[0]) {
                case "exit":
                    System.exit(Integer.parseInt(commands[1]));
                    break;
                case "echo":
                    List<String> echoList = new ArrayList<>(Arrays.asList(commands));

                    // Handle stderr marker
                    if (echoList.size() >= 2 && echoList.get(echoList.size() - 2).equals("__REDIR_ERR__")) {
                        String errFileTmp = echoList.get(echoList.size() - 1);
                        echoList = echoList.subList(0, echoList.size() - 2);
                        commands = echoList.toArray(new String[0]);
                        writeToFile(errFileTmp, "", append);
                    }

                    // detect append again inside echo
                    if (parsed.size() >= 2 && parsed.get(parsed.size() - 2).equals("__APPEND__")) {
                        append = true;
                        outFile = parsed.get(parsed.size() - 1);
                        parsed = parsed.subList(0, parsed.size() - 2);
                        commands = parsed.toArray(new String[0]);
                    }

                    // Build echo output
                    StringBuilder echoOut = new StringBuilder();
                    for (int i = 1; i < commands.length; i++) {
                        if (i > 1) echoOut.append(" ");
                        echoOut.append(commands[i]);
                    }
                    echoOut.append("\n");

                    // If redirect or append
                    if ((redirect || append) && outFile != null) {
                        File target = new File(outFile);
                        File parent = target.getParentFile();

                        // ❗ Directory doesn't exist -> write to /dev/null
                        if (parent != null && !parent.exists()) {
                            try (FileWriter fw = new FileWriter("/dev/null", true)) {
                                fw.write(echoOut.toString());
                            } catch (IOException ignored) {}
                        } else {
                            writeToFile(outFile, echoOut.toString(), append);
                        }

                    } else {
                        // normal echo
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
        boolean append = false;

        List<String> cmdList = new ArrayList<>(Arrays.asList(commands));

        // Detect stderr redirection placeholder
        if (cmdList.size() >= 2 && cmdList.get(cmdList.size() - 2).equals("__REDIR_ERR__")) {
            redirectErr = true;
            errFile = cmdList.get(cmdList.size() - 1);
            cmdList = cmdList.subList(0, cmdList.size() - 2);
            commands = cmdList.toArray(new String[0]);
        }

        // Detect append placeholder
        if (cmdList.size() >= 2 && cmdList.get(cmdList.size() - 2).equals("__APPEND__")) {
            append = true;
            outFile = cmdList.get(cmdList.size() - 1);
            cmdList = cmdList.subList(0, cmdList.size() - 2);
            commands = cmdList.toArray(new String[0]);
        }

        // ✅ Check if redirect target directory exists (silent ignore if not)
        boolean invalidOutputPath = false;
        if (outFile != null) {
            File target = new File(outFile);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                invalidOutputPath = true;
            }
        }

        File nullFile = new File("/dev/null");
        if (invalidOutputPath) {
            // disable real redirect, but discard stdout
            redirect = false;
            append = false;
            outFile = null;

            // force discarding output
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.directory(currentDir);
            pb.redirectOutput(nullFile);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            try {
                Process p = pb.start();
                p.waitFor();
            } catch (Exception ignored) {}
            return;
        }



        for (String dir : dirs) {
            File file = new File(dir, cmd);
            if (file.exists() && file.canExecute()) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(commands);
                    pb.directory(currentDir);

                    // ✅ Apply correct redirection logic
                    if (!invalidOutputPath && append && outFile != null) {
                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(outFile)));
                    } else if (!invalidOutputPath && redirect && outFile != null) {
                        pb.redirectOutput(new File(outFile));
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    // ✅ stderr redirection if needed
                    if (redirectErr && errFile != null) {
                        pb.redirectError(new File(errFile));
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
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
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // toggle single quote
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                if (!inSingle && cur.length() == 0) tokens.add("");
                continue;
            }

            // toggle double quote
            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                if (!inDouble && cur.length() == 0) tokens.add("");
                continue;
            }

            // Backslash handling (only outside single quotes)
            if (c == '\\' && !inSingle) {
                if (inDouble) {
                    if (i + 1 < input.length()) {
                        char next = input.charAt(i + 1);
                        if (next == '"' || next == '\\' || next == '$' || next == '`') {
                            cur.append(next);
                            i++;
                            continue;
                        }
                    }
                    cur.append('\\');
                    continue;
                }

                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == ' ') {
                        cur.append(' ');
                        i++;
                        continue;
                    }
                    cur.append(next);
                    i++;
                    continue;
                }

                cur.append('\\');
                continue;
            }

            // Space ends token only when not quoted
            if (c == ' ' && !inSingle && !inDouble) {
                if (cur.length() > 0) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }

            cur.append(c);
        }

        if (cur.length() > 0) tokens.add(cur.toString());

        // Final result + placeholders for internal redirection markers
        List<String> cleaned = new ArrayList<>();
        String out = null, err = null;
        boolean append = false;

        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);

            // append >>
            if (t.equals(">>") || t.equals("1>>")) {
                append = true;
                out = tokens.get(++i);
                cleaned.add("__APPEND__");
                cleaned.add(out);
                continue;
            }
            if (t.startsWith(">>")) {
                append = true;
                out = t.substring(2);
                cleaned.add("__APPEND__");
                cleaned.add(out);
                continue;
            }
            if (t.startsWith("1>>")) {
                append = true;
                out = t.substring(3);
                cleaned.add("__APPEND__");
                cleaned.add(out);
                continue;
            }

            // normal redirect >
            if (t.equals(">") || t.equals("1>")) {
                out = tokens.get(++i);
                continue;
            }
            if (t.startsWith(">")) {
                out = t.substring(1);
                continue;
            }
            if (t.startsWith("1>")) {
                out = t.substring(2);
                continue;
            }

            // stderr redirect 2>
            if (t.equals("2>")) {
                err = tokens.get(++i);
                continue;
            }
            if (t.startsWith("2>")) {
                err = t.substring(2);
                continue;
            }

            cleaned.add(t);
        }

        if (out != null && !append) {
            cleaned.add("__REDIR__");
            cleaned.add(out);
        }

        if (err != null) {
            cleaned.add("__REDIR_ERR__");
            cleaned.add(err);
        }

        return cleaned;
    }


    static void writeToFile(String file, String content, boolean append) {
        try (FileWriter fw = new FileWriter(file, append)) {
            fw.write(content);
        } catch (IOException e) {
            System.out.println("Error writing to file");
        }
    }

}