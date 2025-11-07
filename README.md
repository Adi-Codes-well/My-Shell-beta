# Java Mini Shell (POSIX Compliant) 🐚

This project is a custom implementation of a POSIX-compliant shell written in Java. It is capable of handling standard shell tasks, including running built-in commands, executing external programs, and managing input/output redirection and pipelines.

## ✨ Features

The shell provides core functionality necessary for interacting with the operating system:

### Built-in Commands
The following essential shell commands are implemented natively in Java:
* **`exit [status]`**: Exits the shell, optionally with a specified status code.
* **`echo [args]`**: Prints arguments to standard output.
* **`type [command]`**: Identifies how a command would be interpreted (as a builtin, or as an external program found in the PATH).
* **`pwd`**: Prints the name of the current working directory.
* **`cd [path]`**: Changes the current working directory, supporting absolute paths and the home directory (`~`) shortcut.

### Core Functionality
* **External Command Execution**: Searches the system's `PATH` to find and execute external programs (e.g., `ls`, `grep`).
* **Command Pipelines**: Supports chaining commands using the pipe operator (`|`), routing the standard output of one command to the standard input of the next.
* **I/O Redirection**: Supports redirecting or appending standard output (`>`, `>>`, `1>`, `1>>`) and standard error (`2>`, `2>>`) to a specified file.

***

## 🛠️ Prerequisites

To build and run this project, you need the following tools installed on your system:

* **Java Development Kit (JDK) 25 or higher:** The project is configured to use the Java 25 language level and preview features.
* **Apache Maven:** Used to manage dependencies and build the executable JAR file.

***

## 🚀 Getting Started

### 1. Build the Executable

Execute the Maven `package` goal from the root directory. This command compiles the code and bundles it into a single executable file named `mini-shell.jar` (based on previous steps to rename the artifact).

```bash
mvn -B package
```
### 2. Run the Shell

The final executable file will be located at `target/mini-shell.jar`.

Once the build is complete, you can start the shell using the Java executable. **Note the mandatory `--enable-preview` flag** to run the code compiled with modern Java features.

```bash
java --enable-preview -jar target/mini-shell.jar
```
# 3. Usage Example

```bash
$ echo "Hello World"
Hello World
$ type echo
echo is a shell builtin
$ pwd
/home/user/my-mini-shell
$cd src/main/java$ pwd
/home/user/my-mini-shell/src/main/java
$ cat Main.java | grep "import"
import java.util.*;
import java.io.*;
```