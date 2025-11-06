import java.util.*;
import java.io.*;

public class Main {

    // Global variable
    static File currentDir = new File(System.getProperty("user.dir"));
    static final String[] BUILTINS = {"echo", "exit"};

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine();

            if (input.contains("\t")) {
                String beforeTab = input.split("\t", 2)[0];
                String completed = handleAutocomplete(input);
                if (!completed.equals(beforeTab)) {
                    System.out.println("$ " + completed);
                    continue;
                }
            }
            List<String> parsed = parseCommand(input);
            if (parsed.isEmpty()) continue;


            String[] commands = parsed.toArray(new String[0]);

            switch (commands[0]) {
                case "exit":
                    if (commands.length > 1) System.exit(Integer.parseInt(commands[1]));
                    else System.exit(0);
                    break;

                case "echo": {
                    // operate on a mutable list copy
                    List<String> echoList = new ArrayList<>(Arrays.asList(commands));

                    // flags & filenames
                    boolean outAppend = false;
                    boolean outRedirect = false;
                    String outFileTmp = null;

                    boolean errAppend = false;
                    String errFileTmp = null;

                    // detect markers at the end repeatedly (allow both stdout+stderr in any order)
                    boolean changed = true;
                    while (changed && echoList.size() >= 2) {
                        changed = false;
                        String marker = echoList.get(echoList.size() - 2);
                        String fname = echoList.get(echoList.size() - 1);
                        if (marker.equals("__APPEND__")) {
                            outAppend = true;
                            outFileTmp = fname;
                            echoList = echoList.subList(0, echoList.size() - 2);
                            changed = true;
                        } else if (marker.equals("__REDIR__")) {
                            outRedirect = true;
                            outFileTmp = fname;
                            echoList = echoList.subList(0, echoList.size() - 2);
                            changed = true;
                        } else if (marker.equals("__APPEND_ERR__")) {
                            errAppend = true;
                            errFileTmp = fname;
                            echoList = echoList.subList(0, echoList.size() - 2);
                            changed = true;
                        } else if (marker.equals("__REDIR_ERR__")) {
                            errAppend = false;
                            errFileTmp = fname;
                            echoList = echoList.subList(0, echoList.size() - 2);
                            changed = true;
                        }
                    }

                    // rebuild commands for printing
                    commands = echoList.toArray(new String[0]);

                    // Build normal echo output
                    StringBuilder echoOut = new StringBuilder();
                    for (int i = 1; i < commands.length; i++) {
                        if (i > 1) echoOut.append(" ");
                        echoOut.append(commands[i]);
                    }
                    echoOut.append("\n");

                    // stderr handling for builtin echo (tests expect builtins to write)
                    if (errFileTmp != null) {
                        File target = new File(errFileTmp);
                        File parent = target.getParentFile();
                        // POSIX behavior: if directory doesn’t exist, silently discard any stderr output
                        if (parent == null || parent.exists()) {
                            try {
                                if (!target.exists()) {
                                    target.createNewFile();
                                }
                            } catch (IOException ignored) {}
                        }
                    }

                    // Always print to stdout unless stdout is redirected
                    if (outFileTmp == null) {
                        System.out.print(echoOut.toString());
                    }

                    // stdout handling for builtin echo
                    if (outFileTmp != null) {
                        File target = new File(outFileTmp);
                        File parent = target.getParentFile();

                        if (parent != null && !parent.exists()) {
                            // discard by writing to /dev/null
                            try (FileWriter fw = new FileWriter("/dev/null", true)) {
                                fw.write(echoOut.toString());
                            } catch (IOException ignored) {}
                        } else {
                            writeToFile(outFileTmp, echoOut.toString(), outAppend);
                        }
                    }

                    break;
                }

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
                    runExternalCommand(commands);
                    break;
            }
        }
    }

    static String handleAutocomplete(String input) {
        String beforeTab = input.split("\t")[0];
        String[] parts = beforeTab.split("\\s+");
        String lastWord = parts[parts.length - 1];

        for (String cmd : BUILTINS) {
            if (cmd.startsWith(lastWord)) {
                return beforeTab.substring(0, beforeTab.length() - lastWord.length()) + cmd + " ";
            }
        }

        return beforeTab;
    }

    static void type(String[] input) {
        String[] validCommands = {"exit", "type", "echo", "pwd", "cd"};
        if (search(validCommands, input[1])) {
            System.out.println(input[1] + " is a shell builtin");
            return;
        }
        String path = System.getenv("PATH");
        if (path == null) path = "";
        String[] dirs = path.split(":");

        for (String dir : dirs) {
            if (dir.isEmpty()) continue;
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
        if (commands.length == 0) return;

        String cmd = commands[0];
        String path = System.getenv("PATH");
        if (path == null) path = "";
        String[] dirs = path.split(":");

        boolean redirectOut = false;
        boolean appendOut = false;
        String outFileName = null;

        boolean redirectErr = false;
        boolean appendErr = false;
        String errFileName = null;

        List<String> cmdList = new ArrayList<>();

        // Scan for redirection markers anywhere in the command
        for (int i = 0; i < commands.length; ) {
            String token = commands[i];

            if (token.equals("__APPEND__") && i + 1 < commands.length) {
                appendOut = true;
                redirectOut = true;
                outFileName = commands[i + 1];
                i += 2;
                continue;
            }

            if (token.equals("__REDIR__") && i + 1 < commands.length) {
                appendOut = false;
                redirectOut = true;
                outFileName = commands[i + 1];
                i += 2;
                continue;
            }

            if (token.equals("__APPEND_ERR__") && i + 1 < commands.length) {
                appendErr = true;
                redirectErr = true;
                errFileName = commands[i + 1];
                i += 2;
                continue;
            }

            if (token.equals("__REDIR_ERR__") && i + 1 < commands.length) {
                appendErr = false;
                redirectErr = true;
                errFileName = commands[i + 1];
                i += 2;
                continue;
            }

            // Normal command tokens
            cmdList.add(token);
            i++;
        }

        // Build target File objects if present
        File outTarget = (outFileName != null) ? new File(outFileName) : null;
        File errTarget = (errFileName != null) ? new File(errFileName) : null;

        try {
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.directory(currentDir);

            // stdout redirection
            if (redirectOut && outTarget != null) {
                File parent = outTarget.getParentFile();
                if (parent != null && !parent.exists()) {
                    // directory doesn't exist -> discard to /dev/null
                    pb.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")));
                } else {
                    if (appendOut) {
                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outTarget));
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.to(outTarget));
                    }
                }
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            // stderr redirection
            if (redirectErr && errTarget != null) {
                File parent = errTarget.getParentFile();
                if (parent != null && !parent.exists()) {
                    // directory doesn't exist -> discard to /dev/null
                    pb.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
                } else {
                    try {
                        if (appendErr && !errTarget.exists()) {
                            errTarget.createNewFile();  // ensure file exists before append
                        }
                    } catch (IOException ignored) {}

                    if (appendErr) {
                        pb.redirectError(ProcessBuilder.Redirect.appendTo(errTarget));
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.to(errTarget));
                    }
                }
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process p = pb.start();
            p.waitFor();
            return;

        } catch (IOException e) {
            // If not found directly (or other IO issue), try PATH search
            for (String dir : dirs) {
                if (dir.isEmpty()) continue;
                File file = new File(dir, cmd);
                if (file.exists() && file.canExecute()) {
                    try {
                        List<String> newCmd = new ArrayList<>(cmdList);
                        newCmd.set(0, file.getAbsolutePath());

                        ProcessBuilder pb2 = new ProcessBuilder(newCmd);
                        pb2.directory(currentDir);

                        // stdout redirection for fallback
                        if (redirectOut && outTarget != null) {
                            File parent = outTarget.getParentFile();
                            if (parent != null && !parent.exists()) {
                                pb2.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")));
                            } else {
                                if (appendOut) pb2.redirectOutput(ProcessBuilder.Redirect.appendTo(outTarget));
                                else pb2.redirectOutput(ProcessBuilder.Redirect.to(outTarget));
                            }
                        } else {
                            pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }

                        // stderr redirection for fallback
                        if (redirectErr && errTarget != null) {
                            File parent = errTarget.getParentFile();
                            if (parent != null && !parent.exists()) {
                                pb2.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
                            } else  {
                                try {
                                    // Ensure file exists before appending (POSIX behavior for "2>>")
                                    if (appendErr && !errTarget.exists()) {
                                        errTarget.createNewFile();
                                    }
                                } catch (IOException ignored) {}

                                if (appendErr) {
                                    pb2.redirectError(ProcessBuilder.Redirect.appendTo(errTarget));
                                } else {
                                    pb2.redirectError(ProcessBuilder.Redirect.to(errTarget));
                                }
                            }
                        } else {
                            pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }

                        Process p2 = pb2.start();
                        p2.waitFor();
                        return;
                    } catch (Exception ignored) {}
                }
            }
            System.out.println(cmd + ": command not found");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ex);
        }
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
            if (home == null) home = System.getProperty("user.home");
            newDir = new File(home);
        } else {
            newDir = new File(currentDir, path);
        }

        try {
            if (newDir.exists() && newDir.isDirectory()) {
                currentDir = newDir.getCanonicalFile(); // normalize path
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
        boolean appendOut = false, appendErr = false;

        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);

            // append stdout >> or 1>>
            if (t.equals(">>") || t.equals("1>>")) {
                appendOut = true;
                out = tokens.get(++i);
                cleaned.add("__APPEND__");
                cleaned.add(out);
                continue;
            }
            if (t.startsWith(">>")) {
                appendOut = true;
                out = t.substring(2);
                cleaned.add("__APPEND__");
                cleaned.add(out);
                continue;
            }
            if (t.startsWith("1>>")) {
                appendOut = true;
                out = t.substring(3);
                cleaned.add("__APPEND__");
                cleaned.add(out);
                continue;
            }

            // append stderr 2>>
            if (t.equals("2>>")) {
                appendErr = true;
                err = tokens.get(++i);
                cleaned.add("__APPEND_ERR__");
                cleaned.add(err);
                continue;
            }
            if (t.startsWith("2>>")) {
                appendErr = true;
                err = t.substring(3);
                cleaned.add("__APPEND_ERR__");
                cleaned.add(err);
                continue;
            }

            // normal stdout redirect > or 1>
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

        // Handle stdout overwrite (>) if not append
        if (out != null && !appendOut) {
            cleaned.add("__REDIR__");
            cleaned.add(out);
        }

        // Handle stderr overwrite (2>) if not append AND no stderr redirection exists
        boolean hasErrRedir = cleaned.contains("__APPEND_ERR__") || cleaned.contains("__REDIR_ERR__");
        if (err != null && !hasErrRedir) {
            if (appendErr) {
                cleaned.add("__APPEND_ERR__");
                cleaned.add(err);
            } else {
                cleaned.add("__REDIR_ERR__");
                cleaned.add(err);
            }
        }

        return cleaned;
    }

    static void writeToFile(String file, String content, boolean append) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, append))) {
            bw.write(content);
            bw.flush(); // ensure it's written immediately
        } catch (IOException e) {
            // do NOT print errors to stdout, silent fail per POSIX shell
        }
    }
}
