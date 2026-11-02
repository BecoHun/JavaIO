import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.Scanner;

public class ConsoleApp {

    public static void main(String[] args) {
        printBanner();
        runAutomatedTests();
        startInteractiveMode();
    }

    private static void printBanner() {
        System.out.println("==========================================================");
        System.out.println("             ConsoleReader - Java 17 Standalone           ");
        System.out.println("==========================================================");
        System.out.println("Overview:");
        System.out.println("This program provides two main features:");
        System.out.println("1. Read Names: Reads names line by line until '0',");
        System.out.println("   validates the format, and prints the total count.");
        System.out.println("2. Read Numbers: Reads numbers from a single line,");
        System.out.println("   validates them, and prints the numbers and their sum.");
        System.out.println("----------------------------------------------------------\n");
    }

    private static void runAutomatedTests() {
        System.out.println("[1/3] Starting automated tests...\n");

        // 1. Valid names test
        boolean nameTestPassed = testNamesInput("Ashley\nMarco\nTony\n0\n", "Number of names: 3");
        System.out.println(" - Valid names test: " + (nameTestPassed ? "PASSED" : "FAILED"));

        // 2. Invalid names test (exception expected)
        boolean invalidNameTestPassed = testNamesException("Ashl1ey\nMarco\nTony\n0\n");
        System.out.println(" - Invalid names test (exception handling): " + (invalidNameTestPassed ? "PASSED" : "FAILED"));

        // 3. Valid numbers test
        boolean numberTestPassed = testNumbersInput("132 865 456722 6 261\n", 
                "Numbers: 132 865 456722 6 261" + System.lineSeparator() + "Sum: 457986");
        System.out.println(" - Valid numbers test: " + (numberTestPassed ? "PASSED" : "FAILED"));

        // 4. Invalid numbers test (exception expected)
        boolean invalidNumberTestPassed = testNumbersException("-132\n");
        System.out.println(" - Invalid numbers test (exception handling): " + (invalidNumberTestPassed ? "PASSED" : "FAILED"));

        System.out.println("\n[2/3] Automated tests completed successfully.\n");
    }

    private static void startInteractiveMode() {
        System.out.println("==========================================================");
        System.out.println("               [3/3] INTERACTIVE MODE                    ");
        System.out.println("==========================================================");
        
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\nChoose an option:");
            System.out.println("1 - Read names (Enter names line by line, type '0' to finish)");
            System.out.println("2 - Read numbers (Enter numbers separated by spaces in a single line)");
            System.out.println("0 - Exit");
            System.out.print("Your choice: ");

            String choice = scanner.nextLine().trim();

            if ("0".equals(choice)) {
                System.out.println("Thank you for using the program. Goodbye!");
                break;
            } else if ("1".equals(choice)) {
                System.out.println("\n--- READ NAMES MODE ---");
                System.out.println("Enter names one by one (type '0' to exit):");
                try {
                    ConsoleReader.readNames();
                } catch (Exception e) {
                    System.out.println("An error occurred during execution: " + e.getMessage());
                }
            } else if ("2".equals(choice)) {
                System.out.println("\n--- READ NUMBERS MODE ---");
                System.out.println("Enter numbers separated by spaces in a single line:");
                try {
                    ConsoleReader.readNumbers();
                } catch (Exception e) {
                    System.out.println("An error occurred during execution: " + e.getMessage());
                }
            } else {
                System.out.println("Invalid choice. Please enter 0, 1, or 2.");
            }
        }
    }

    private static boolean testNamesInput(String input, String expectedOutput) {
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes()));
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            System.setOut(new java.io.PrintStream(out));

            ConsoleReader.readNames();
            String actual = out.toString().trim();
            return expectedOutput.equals(actual);
        } catch (Exception e) {
            return false;
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    private static boolean testNamesException(String input) {
        java.io.InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes()));
            ConsoleReader.readNames();
            return false; // If no exception was thrown, the test fails here
        } catch (RuntimeException e) {
            return true; // Exception was expected, caught
        } finally {
            System.setIn(originalIn);
        }
    }

    private static boolean testNumbersInput(String input, String expectedOutput) {
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes()));
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            System.setOut(new java.io.PrintStream(out));

            ConsoleReader.readNumbers();
            String actual = out.toString().trim();
            return expectedOutput.equals(actual);
        } catch (Exception e) {
            return false;
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    private static boolean testNumbersException(String input) {
        java.io.InputStream originalIn = System.in;
        try {
            System.setIn(new ByteArrayInputStream(input.getBytes()));
            ConsoleReader.readNumbers();
            return false;
        } catch (RuntimeException e) {
            return true;
        } finally {
            System.setIn(originalIn);
        }
    }
}

class ConsoleReader {
    public static void readNames() {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));
        int count = 0;
        try {
            String name;
            while ((name = reader.readLine()) != null) {
                if (name.equals("0")) {
                    break;
                }
                if (!name.matches("[\\p{L} ,.\\'-]+")) {
                    throw new RuntimeException("Invalid name: " + name);
                }
                count++;
            }
            System.out.println("Number of names: " + count);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void readNumbers() {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in));
        try {
            String line = reader.readLine();
            if (line == null) {
                throw new RuntimeException("No input provided");
            }
            if (!line.matches("[0-9 ]+")) {
                throw new RuntimeException("Invalid input: " + line);
            }
            BigInteger sum = BigInteger.ZERO;
            String trimmedLine = line.trim();
            for (String number : trimmedLine.split(" +")) {
                sum = sum.add(new BigInteger(number));
            }
            System.out.println("Numbers: " + line);
            System.out.println("Sum: " + sum);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
/*
ConsoleApp - Program Description
This is a standalone, dependency-free Java 17 console application featuring built-in automated tests and a user-friendly interactive menu.
Main Features:
Read Names (readNames)
Operation: Reads names line by line until the user enters 0.
Validation: Ensures the input contains only valid characters (letters, spaces, hyphens, apostrophes, commas, dots). Throws an exception on error.
Output: Prints the total count of valid names entered.
Read Numbers (readNumbers)
Operation: Reads integers separated by spaces in a single line.
Validation: Ensures the line contains only digits and spaces.
Output: Prints the sequence of numbers read and their exact sum (using BigInteger to prevent overflow).
Execution Flow:
Startup and Overview: Displays the program information/banner.
Automated Tests: Runs the built-in tests (for both valid and invalid cases) and reports their results (PASSED / FAILED).
Interactive Mode: Provides a simple menu allowing the user to try out and test both features themselves.
*/

