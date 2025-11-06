import java.io.*;
import java.util.*;

public class Main {

    static File currentDir = new File(System.getProperty("user.dir"));

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("$ ");
            if (!scanner.hasNextLine()) break;

            String input = scanner.nextLine().trim();
            if (input.isEmpty()) continue;

            List<String> parsed = parseCommand(input);

            int pipeIndex = parsed.indexOf("|");
            if (pipeIndex != -1) {
                List<String> left = parsed.subList(0, pipeIndex);
                List<String> right = parsed.subList(pipeIndex + 1, parsed.size());
                if (left.isEmpty() || right.isEmpty()) continue;
                runPipeline(left, right);
                continue;
            }

            String[] cmd = parsed.toArray(new String[0]);
            if (cmd.length == 0) continue;

            if (cmd[0].equals("exit")) System.exit(0);
            else if (cmd[0].equals("pwd")) System.out.println(currentDir.getAbsolutePath());
            else if (cmd[0].equals("cd")) cd(cmd);
            else runExternal(cmd);
        }
    }

    static void runPipeline(List<String> leftCmd, List<String> rightCmd) {
        try {
            ProcessBuilder pb1 = new ProcessBuilder(leftCmd);
            ProcessBuilder pb2 = new ProcessBuilder(rightCmd);
            pb1.directory(currentDir);
            pb2.directory(currentDir);

            pb1.redirectError(ProcessBuilder.Redirect.INHERIT);
            pb2.redirectError(ProcessBuilder.Redirect.INHERIT);
            pb1.redirectOutput(ProcessBuilder.Redirect.PIPE);
            pb2.redirectInput(ProcessBuilder.Redirect.PIPE);
            pb2.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            Process p1 = startProcess(pb1, leftCmd.get(0));
            Process p2 = startProcess(pb2, rightCmd.get(0));

            Thread pipeThread = new Thread(() -> {
                try (InputStream in = p1.getInputStream();
                     OutputStream out = p2.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                        out.flush();
                    }
                } catch (IOException ignored) {}
                try { p2.getOutputStream().close(); } catch (IOException ignored) {}
            });

            pipeThread.start();
            p1.waitFor();
            pipeThread.join();
            p2.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static Process startProcess(ProcessBuilder pb, String cmd) {
        try {
            return pb.start();
        } catch (IOException e) {
            String path = System.getenv("PATH");
            if (path == null) path = "";
            for (String dir : path.split(":")) {
                if (dir.isEmpty()) continue;
                File f = new File(dir, cmd);
                if (f.exists() && f.canExecute()) {
                    pb.command().set(0, f.getAbsolutePath());
                    try {
                        return pb.start();
                    } catch (IOException ignored) {}
                }
            }
            System.out.println(cmd + ": command not found");
            return null;
        }
    }

    static void runExternal(String[] input) {
        List<String> cmdList = new ArrayList<>();
        boolean appendOut = false;
        boolean redirectOut = false;
        String outFile = null;

        for (int i = 0; i < input.length; i++) {
            String token = input[i];
            if (token.equals(">") || token.equals("1>")) {
                redirectOut = true;
                if (i + 1 < input.length) outFile = input[++i];
            } else if (token.equals(">>") || token.equals("1>>")) {
                appendOut = true;
                redirectOut = true;
                if (i + 1 < input.length) outFile = input[++i];
            } else cmdList.add(token);
        }

        if (cmdList.isEmpty()) return;

        try {
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.directory(currentDir);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);

            if (redirectOut && outFile != null) {
                File target = new File(outFile);
                File parent = target.getParentFile();

                if (parent != null && !parent.exists()) {
                    pb.redirectOutput(ProcessBuilder.Redirect.to(new File("/dev/null")));
                } else {
                    if (appendOut)
                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(target));
                    else
                        pb.redirectOutput(ProcessBuilder.Redirect.to(target));
                }
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            Process p = startProcess(pb, cmdList.get(0));
            if (p != null) p.waitFor();

        } catch (Exception e) {
            System.out.println(input[0] + ": command not found");
        }
    }

    static void cd(String[] cmd) {
        if (cmd.length < 2) return;
        File newDir;
        if (cmd[1].startsWith("/")) newDir = new File(cmd[1]);
        else if (cmd[1].startsWith("~"))
            newDir = new File(System.getProperty("user.home"));
        else newDir = new File(currentDir, cmd[1]);
        if (newDir.exists() && newDir.isDirectory()) {
            try {
                currentDir = newDir.getCanonicalFile();
            } catch (IOException ignored) {}
        } else {
            System.out.println("cd: " + cmd[1] + ": No such file or directory");
        }
    }

    static List<String> parseCommand(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false, inDouble = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (c == '\'' && !inDouble) { inSingle = !inSingle; continue; }
            if (c == '"' && !inSingle) { inDouble = !inDouble; continue; }

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

        // 🔧 Split redirection operators if attached (like nonexistent>>file)
        List<String> splitTokens = new ArrayList<>();
        for (String t : tokens) {
            if (t.contains(">>")) {
                int idx = t.indexOf(">>");
                String before = t.substring(0, idx);
                String after = t.substring(idx + 2);
                if (!before.isEmpty()) splitTokens.add(before);
                splitTokens.add(">>");
                if (!after.isEmpty()) splitTokens.add(after);
            } else if (t.contains(">")) {
                int idx = t.indexOf(">");
                String before = t.substring(0, idx);
                String after = t.substring(idx + 1);
                if (!before.isEmpty()) splitTokens.add(before);
                splitTokens.add(">");
                if (!after.isEmpty()) splitTokens.add(after);
            } else if (t.contains("2>>")) {
                int idx = t.indexOf("2>>");
                String before = t.substring(0, idx);
                String after = t.substring(idx + 3);
                if (!before.isEmpty()) splitTokens.add(before);
                splitTokens.add("2>>");
                if (!after.isEmpty()) splitTokens.add(after);
            } else if (t.contains("2>")) {
                int idx = t.indexOf("2>");
                String before = t.substring(0, idx);
                String after = t.substring(idx + 2);
                if (!before.isEmpty()) splitTokens.add(before);
                splitTokens.add("2>");
                if (!after.isEmpty()) splitTokens.add(after);
            } else {
                splitTokens.add(t);
            }
        }

        // ✅ Now reuse your existing redirection marker logic
        List<String> cleaned = new ArrayList<>();
        String out = null, err = null;
        boolean appendOut = false, appendErr = false;

        for (int i = 0; i < splitTokens.size(); i++) {
            String t = splitTokens.get(i);

            if (t.equals(">>") || t.equals("1>>")) {
                appendOut = true;
                out = splitTokens.get(++i);
                cleaned.add("__APPEND__");
                cleaned.add(out);
                continue;
            }
            if (t.equals(">") || t.equals("1>")) {
                out = splitTokens.get(++i);
                cleaned.add("__REDIR__");
                cleaned.add(out);
                continue;
            }
            if (t.equals("2>>")) {
                appendErr = true;
                err = splitTokens.get(++i);
                cleaned.add("__APPEND_ERR__");
                cleaned.add(err);
                continue;
            }
            if (t.equals("2>")) {
                err = splitTokens.get(++i);
                cleaned.add("__REDIR_ERR__");
                cleaned.add(err);
                continue;
            }

            cleaned.add(t);
        }

        return cleaned;
    }

}
