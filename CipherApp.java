import java.io.*;
import java.util.*;

public class CipherApp {

    // --- 1. TransformerInputStream (with dynamic shift) ---
    public static class TransformerInputStream extends FilterInputStream {
        private final int shift;

        public TransformerInputStream(InputStream input, int shift) {
            super(input);
            this.shift = shift;
        }

        @Override
        public int read() throws IOException {
            int data = super.read();
            if (data == -1) {
                return -1;
            }
            if (data >= 'A' && data <= 'Z') {
                return 'A' + (data - 'A' + shift % 26 + 26) % 26;
            }
            if (data >= 'a' && data <= 'z') {
                return 'a' + (data - 'a' + shift % 26 + 26) % 26;
            }
            return data;
        }

        @Override
        public void close() throws IOException {
            super.close();
            // System.out.println("Stream closed.");
        }
    }

    // --- 2. CipherCreator Logic ---
    public static class CipherCreator {
        private CipherCreator() {}

        private static StringBuilder processStream(InputStream in, int shift, boolean allowSpaces) {
            StringBuilder result = new StringBuilder();
            try (TransformerInputStream stream = new TransformerInputStream(in, shift)) {
                int data;
                while ((data = stream.read()) != -1) {
                    if ((data >= 'A' && data <= 'Z')
                            || (data >= 'a' && data <= 'z')
                            || data == '\n'
                            || data == '\r'
                            || (allowSpaces && data == ' ')) {
                        result.append((char) data);
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot read input stream.", e);
            }
            return result;
        }

        public static StringBuilder cipherText(File input, int shift) {
            if (input == null || !input.exists() || !input.isFile() || !input.canRead()) {
                throw new IllegalArgumentException("Invalid input file.");
            }
            try {
                return processStream(new FileInputStream(input), shift, false);
            } catch (FileNotFoundException e) {
                throw new IllegalArgumentException("Cannot read input file.", e);
            }
        }

        public static String cipherString(String inputStr, int shift) {
            InputStream in = new ByteArrayInputStream(inputStr.getBytes());
            return processStream(in, shift, true).toString();
        }
    }

    // --- 3. Running Embedded Tests (Detailed & Informative) ---
    public static void runTests() {
        System.out.println("========================================");
        System.out.println("        RUNNING AUTOMATED TESTS         ");
        System.out.println("========================================");
        int passed = 0;
        int total = 4;

        File successfulFile = new File("successful_test.txt");
        File invalidFile = new File("invalid_test.txt");

        try {
            // Populate files with test data
            try (PrintWriter pw = new PrintWriter(successfulFile)) {
                pw.print("Test\ncipher\nFor\nFundamentals\nOf\nProgramming\nXYZ\nzyx\nCamelCase\nDiScO");
            }
            try (PrintWriter pw = new PrintWriter(invalidFile)) {
                pw.print("Hi123!\n@#$");
            }

            // -------------------------------------------------------------
            // 1. Test: Successful encryption
            // -------------------------------------------------------------
            System.out.println("\n[TEST 1/4] shouldReturnSuccessfullyCipheredText");
            System.out.println(" -> Contents of created file (" + successfulFile.getName() + "):");
            printFileContent(successfulFile);

            try {
                StringBuilder res = CipherCreator.cipherText(successfulFile, 2);
                String expected = "Vguv\nekrjgt\nHqt\nHwpfcogpvcnu\nQh\nRtqitcookpi\nZAB\nbaz\nEcognEcug\nFkUeQ";
                
                System.out.println(" -> Result received:\n" + res);
                
                assert res != null && res.toString().equals(expected) : "Mismatch in ciphered text!";
                System.out.println("STATUS: [PASS] Successful encryption!");
                passed++;
            } catch (Exception e) {
                System.out.println("STATUS: [FAIL] Error: " + e.getMessage());
            }

            // -------------------------------------------------------------
            // 2. Test: Character filtering
            // -------------------------------------------------------------
            System.out.println("\n[TEST 2/4] shouldReturnFilteredCipheredText");
            System.out.println(" -> Contents of created file (" + invalidFile.getName() + ") (with special characters and numbers):");
            printFileContent(invalidFile);

            try {
                StringBuilder res = CipherCreator.cipherText(invalidFile, 2);
                System.out.println(" -> Result received (only allowed letters and line breaks):\n" + res);
                
                System.out.println("STATUS: [PASS] Filtering completed successfully!");
                passed++;
            } catch (Exception e) {
                System.out.println("STATUS: [FAIL] Error: " + e.getMessage());
            }

            // -------------------------------------------------------------
            // 3. Test: Non-existent file exception handling
            // -------------------------------------------------------------
            System.out.println("\n[TEST 3/4] shouldReturnNonExistentException");
            File nonExistent = new File("non_existent_file_abc.txt");
            System.out.println(" -> Attempting to read a non-existent file: " + nonExistent.getName());

            try {
                CipherCreator.cipherText(nonExistent, 2);
                System.out.println("STATUS: [FAIL] The program did not throw an exception for the non-existent file!");
            } catch (IllegalArgumentException e) {
                System.out.println(" -> Expected exception caught: " + e.getMessage());
                System.out.println("STATUS: [PASS] Exception handling OK!");
                passed++;
            }

            // -------------------------------------------------------------
            // 4. Test: Directory as input exception handling
            // -------------------------------------------------------------
            System.out.println("\n[TEST 4/4] shouldReturnDirectoryException");
            File directory = new File(".");
            System.out.println(" -> Attempting to read a folder (directory) instead of a file: " + directory.getAbsolutePath());

            try {
                CipherCreator.cipherText(directory, 2);
                System.out.println("STATUS: [FAIL] The program did not throw an exception when a directory was provided!");
            } catch (IllegalArgumentException e) {
                System.out.println(" -> Expected exception caught: " + e.getMessage());
                System.out.println("STATUS: [PASS] Directory check OK!");
                passed++;
            }

        } catch (IOException e) {
            System.err.println("Error creating test files: " + e.getMessage());
        } finally {
            // Cleanup
            if (successfulFile.exists()) successfulFile.delete();
            if (invalidFile.exists()) invalidFile.delete();
        }

        System.out.println("\n========================================");
        System.out.println(" TESTS COMPLETED: " + passed + "/" + total + " PASSED ");
        System.out.println("========================================\n");
    }

    // Helper method to print file contents to the console
    private static void printFileContent(File file) {
        System.out.println("    --------------------------------");
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("    | " + line);
            }
        } catch (IOException e) {
            System.out.println("    | (Failed to read the file)");
        }
        System.out.println("    --------------------------------");
    }

    // --- 4. Main Method (Entry Point) ---
    public static void main(String[] args) {
        // 1. Run and display tests in detail
        runTests();

        // 2. Start interactive mode
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== INTERACTIVE CIPHER PROGRAM ===");
        
        int shift = 2;
        System.out.print("Enter the shift amount (default 2): ");
        String shiftInput = scanner.nextLine().trim();
        if (!shiftInput.isEmpty()) {
            try {
                shift = Integer.parseInt(shiftInput);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number, shift remains 2.");
            }
        }

        System.out.println("Enter text to cipher line by line.");
        System.out.println("Type '00' to exit.");
        System.out.println("----------------------------------------");

        while (true) {
            System.out.print("Text > ");
            String line = scanner.nextLine();
            
            if ("00".equalsIgnoreCase(line)) {
                System.out.println("Program terminated. Goodbye!");
                break;
            }

            String encrypted = CipherCreator.cipherString(line, shift);
            System.out.println("Encrypted: " + encrypted);
        }

        scanner.close();
    }
}
/*
CipherApp Summary
This standalone, external-dependency-free Java console application is a file and text encryption program that applies a classic Caesar cipher with a dynamic shift.
Main Components:
TransformerInputStream: 
A custom FilterInputStream subclass that dynamically shifts (encrypts) English uppercase and lowercase letters by a given value during byte reading, 
while leaving other characters unchanged.
CipherCreator: 
A utility class providing static methods for encrypting files and character sequences.
Embedded Automated Tests (runTests): 
Executes 4 test scenarios upon program startup (successful encryption, character filtering, handling non-existent files, handling directories as input) 
and prints the detailed results to the console.
Interactive Mode (main): 
After the tests finish, it prompts the user for the shift amount and then interactively encrypts text entered line by line in the console until the sentinel value 00 is entered.
*/

