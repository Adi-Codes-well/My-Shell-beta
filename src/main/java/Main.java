import java.util.*;
import java.io.*;

public class Main {

    static File currentDir = new File(System.getProperty("user.dir"));

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;
            String input = scanner.nextLine();
            List<String> parsed = parseCommand(input);

            int pipeIndex = parsed.indexOf("|");
            if (pipeIndex != -1) {
                List<String> leftCmd = new ArrayList<>(parsed.subList(0, pipeIndex));
                List<String> rightCmd = new ArrayList<>(parsed.subList(pipeIndex + 1, parsed.size()));
                if (leftCmd.isEmpty() || rightCmd.isEmpty()) {
                    System.err.println("Invalid pipeline");
                    continue;
                }
                runPipeline(leftCmd, rightCmd);
                continue;
            }

            String[] commands = parsed.toArray(new String[0]);
            if (commands.length == 0) continue;

            switch (commands[0]) {
                case "exit":
                    if (commands.length > 1) System.exit(Integer.parseInt(commands[1]));
                    else System.exit(0);
                    break;
                case "echo": {
                    // builtin echo (kept same behavior as before)
                    List<String> echoList = new ArrayList<>(Arrays.asList(commands));
                    boolean outAppend = false;
                    boolean outRedirect = false;
                    String outFileTmp = null;
                    boolean errAppend = false;
                    String errFileTmp = null;

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

                    commands = echoList.toArray(new String[0]);
                    StringBuilder echoOut = new StringBuilder();
                    for (int i = 1; i < commands.length; i++) {
                        if (i > 1) echoOut.append(" ");
                        echoOut.append(commands[i]);
                    }
                    echoOut.append("\n");

                    if (errFileTmp != null) {
                        File target = new File(errFileTmp);
                        File parent = target.getParentFile();
                        if (parent == null || parent.exists()) {
                            try {
                                if (!target.exists()) target.createNewFile();
                            } catch (IOException ignored) {}
                        }
                    }

                    if (outFileTmp == null) {
                        System.out.print(echoOut.toString());
                    }

                    if (outFileTmp != null) {
                        File target = new File(outFileTmp);
                        File parent = target.getParentFile();
                        if (parent != null && !parent.exists()) {
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

    // run a two-command pipeline
    static void runPipeline(List<String> leftTokens, List<String> rightTokens) {
        // parse left tokens for redirection markers and build command lists
        ParsedCommand left = buildCommandFromTokens(leftTokens);
        ParsedCommand right = buildCommandFromTokens(rightTokens);

        try {
            ProcessBuilder pb1 = new ProcessBuilder(left.cmdList);
            ProcessBuilder pb2 = new ProcessBuilder(right.cmdList);
            pb1.directory(currentDir);
            pb2.directory(currentDir);

            // stdout for left: either redirected to file or pipe to pb2
            if (left.redirectOut && left.outTarget != null) {
                File parent = left.outTarget.getParentFile();
                if (parent != null && !parent.exists()) {
                    pb1.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")));
                } else {
                    if (left.appendOut) pb1.redirectOutput(ProcessBuilder.Redirect.appendTo(left.outTarget));
                    else pb1.redirectOutput(ProcessBuilder.Redirect.to(left.outTarget));
                }
            } else {
                pb1.redirectOutput(ProcessBuilder.Redirect.PIPE);
            }

            // stderr for left
            if (left.redirectErr && left.errTarget != null) {
                File parent = left.errTarget.getParentFile();
                if (parent != null && !parent.exists()) {
                    pb1.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
                } else {
                    if (left.appendErr) pb1.redirectError(ProcessBuilder.Redirect.appendTo(left.errTarget));
                    else pb1.redirectError(ProcessBuilder.Redirect.to(left.errTarget));
                }
            } else {
                pb1.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            // stdin for right: from pipe unless we decide to change (no '<' support currently)
            pb2.redirectInput(ProcessBuilder.Redirect.PIPE);

            // stdout for right: either redirected or inherit
            if (right.redirectOut && right.outTarget != null) {
                File parent = right.outTarget.getParentFile();
                if (parent != null && !parent.exists()) {
                    pb2.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")));
                } else {
                    if (right.appendOut) pb2.redirectOutput(ProcessBuilder.Redirect.appendTo(right.outTarget));
                    else pb2.redirectOutput(ProcessBuilder.Redirect.to(right.outTarget));
                }
            } else {
                pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            // stderr for right
            if (right.redirectErr && right.errTarget != null) {
                File parent = right.errTarget.getParentFile();
                if (parent != null && !parent.exists()) {
                    pb2.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
                } else {
                    if (right.appendErr) pb2.redirectError(ProcessBuilder.Redirect.appendTo(right.errTarget));
                    else pb2.redirectError(ProcessBuilder.Redirect.to(right.errTarget));
                }
            } else {
                pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process p1 = startWithPathFallback(pb1, left.cmdList.get(0));
            Process p2 = startWithPathFallback(pb2, right.cmdList.get(0));

            // connect p1 stdout -> p2 stdin if p1 stdout is pipe and p2 stdin is pipe
            Thread pipeThread = null;
            if (p1 != null && p2 != null) {
                InputStream in = p1.getInputStream();
                OutputStream out = p2.getOutputStream();
                pipeThread = new Thread(() -> {
                    try (InputStream rin = in; OutputStream rout = out) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = rin.read(buf)) != -1) {
                            rout.write(buf, 0, n);
                            rout.flush();
                        }
                    } catch (IOException ignored) {}
                });
                pipeThread.start();
            }

            if (p1 != null) p1.waitFor();
            if (p2 != null) {
                try { p2.getOutputStream().close(); } catch (IOException ignored) {}
            }
            if (pipeThread != null) pipeThread.join();
            if (p2 != null) p2.waitFor();

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // helper to attempt to start a process, fallback to PATH search like runExternalCommand
    static Process startWithPathFallback(ProcessBuilder pb, String cmd) {
        try {
            return pb.start();
        } catch (IOException e) {
            String path = System.getenv("PATH");
            if (path == null) path = "";
            String[] dirs = path.split(":");
            for (String dir : dirs) {
                if (dir.isEmpty()) continue;
                File file = new File(dir, cmd);
                if (file.exists() && file.canExecute()) {
                    List<String> cmdlist = new ArrayList<>(pb.command());
                    cmdlist.set(0, file.getAbsolutePath());
                    ProcessBuilder pb2 = new ProcessBuilder(cmdlist);
                    pb2.directory(pb.directory());
                    pb2.redirectOutput(pb.redirectOutput());
                    pb2.redirectError(pb.redirectError());
                    pb2.redirectInput(pb.redirectInput());
                    try {
                        return pb2.start();
                    } catch (IOException ignored) {}
                }
            }
            System.out.println(cmd + ": command not found");
            return null;
        }
    }

    // small struct-like holder
    static class ParsedCommand {
        List<String> cmdList = new ArrayList<>();
        boolean redirectOut = false;
        boolean appendOut = false;
        File outTarget = null;
        boolean redirectErr = false;
        boolean appendErr = false;
        File errTarget = null;
    }

    // build ParsedCommand from tokens (handles your internal markers)
    static ParsedCommand buildCommandFromTokens(List<String> tokens) {
        ParsedCommand pc = new ParsedCommand();
        for (int i = 0; i < tokens.size();) {
            String t = tokens.get(i);
            if (t.equals("__APPEND__") && i + 1 < tokens.size()) {
                pc.appendOut = true;
                pc.redirectOut = true;
                pc.outTarget = new File(tokens.get(i + 1));
                i += 2;
                continue;
            }
            if (t.equals("__REDIR__") && i + 1 < tokens.size()) {
                pc.appendOut = false;
                pc.redirectOut = true;
                pc.outTarget = new File(tokens.get(i + 1));
                i += 2;
                continue;
            }
            if (t.equals("__APPEND_ERR__") && i + 1 < tokens.size()) {
                pc.appendErr = true;
                pc.redirectErr = true;
                pc.errTarget = new File(tokens.get(i + 1));
                i += 2;
                continue;
            }
            if (t.equals("__REDIR_ERR__") && i + 1 < tokens.size()) {
                pc.appendErr = false;
                pc.redirectErr = true;
                pc.errTarget = new File(tokens.get(i + 1));
                i += 2;
                continue;
            }
            pc.cmdList.add(t);
            i++;
        }
        return pc;
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
            cmdList.add(token);
            i++;
        }

        File outTarget = (outFileName != null) ? new File(outFileName) : null;
        File errTarget = (errFileName != null) ? new File(errFileName) : null;

        try {
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.directory(currentDir);

            if (redirectOut && outTarget != null) {
                File parent = outTarget.getParentFile();
                if (parent != null && !parent.exists()) {
                    pb.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")));
                } else {
                    if (appendOut) pb.redirectOutput(ProcessBuilder.Redirect.appendTo(outTarget));
                    else pb.redirectOutput(ProcessBuilder.Redirect.to(outTarget));
                }
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (redirectErr && errTarget != null) {
                File parent = errTarget.getParentFile();
                if (parent != null && !parent.exists()) {
                    pb.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
                } else {
                    try {
                        if (appendErr && !errTarget.exists()) errTarget.createNewFile();
                    } catch (IOException ignored) {}
                    if (appendErr) pb.redirectError(ProcessBuilder.Redirect.appendTo(errTarget));
                    else pb.redirectError(ProcessBuilder.Redirect.to(errTarget));
                }
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            Process p = pb.start();
            p.waitFor();
            return;

        } catch (IOException e) {
            for (String dir : dirs) {
                if (dir.isEmpty()) continue;
                File file = new File(dir, cmd);
                if (file.exists() && file.canExecute()) {
                    try {
                        List<String> newCmd = new ArrayList<>(cmdList);
                        newCmd.set(0, file.getAbsolutePath());

                        ProcessBuilder pb2 = new ProcessBuilder(newCmd);
                        pb2.directory(currentDir);

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

                        if (redirectErr && errTarget != null) {
                            File parent = errTarget.getParentFile();
                            if (parent != null && !parent.exists()) {
                                pb2.redirectError(ProcessBuilder.Redirect.to(new File("/dev/null")));
                            } else  {
                                try {
                                    if (appendErr && !errTarget.exists()) errTarget.createNewFile();
                                } catch (IOException ignored) {}
                                if (appendErr) pb2.redirectError(ProcessBuilder.Redirect.appendTo(errTarget));
                                else pb2.redirectError(ProcessBuilder.Redirect.to(errTarget));
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
        if (commands.length < 2) return;
        String path = commands[1];
        File newDir;
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
                currentDir = newDir.getCanonicalFile();
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

            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
                if (!inSingle && cur.length() == 0) tokens.add("");
                continue;
            }

            if (c == '"' && !inSingle) {
                inDouble = !inDouble;
                if (!inDouble && cur.length() == 0) tokens.add("");
                continue;
            }

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

        List<String> cleaned = new ArrayList<>();
        String out = null, err = null;
        boolean appendOut = false, appendErr = false;

        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);

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

        if (out != null && !appendOut) {
            cleaned.add("__REDIR__");
            cleaned.add(out);
        }

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
            bw.flush();
        } catch (IOException e) {
            // silent
        }
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
            if (s.equals(command)) return true;
        }
        return false;
    }
}
