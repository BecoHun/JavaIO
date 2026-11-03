import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public class SymbolsDistributorStandalone {

    // --- 1. Interface ---
    public interface SymbolsDistributor {
        void distribute(File inputFile, Map<Predicate<Integer>, File> outputMapping);
    }

    // --- 2. Implementation ---
    public static class SymbolsDistributorImpl implements SymbolsDistributor {
        @Override
        public void distribute(File inputFile, Map<Predicate<Integer>, File> outputMapping) {
            if (!inputFile.exists() || !inputFile.isFile() || !inputFile.canRead()) {
                throw new IllegalArgumentException("Invalid input file: " + inputFile.getAbsolutePath());
            }

            Map<Predicate<Integer>, BufferedWriter> writers = new HashMap<>();

            try {
                // Prepare output files and writers
                for (Map.Entry<Predicate<Integer>, File> entry : outputMapping.entrySet()) {
                    File outputFile = entry.getValue();
                    File parent = outputFile.getParentFile();

                    if (parent != null) {
                        parent.mkdirs();
                    }

                    if (!outputFile.exists()) {
                        outputFile.createNewFile();
                    }

                    writers.put(entry.getKey(), new BufferedWriter(new FileWriter(outputFile)));
                }

                // Read input and distribute characters
                try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
                    int character;
                    while ((character = reader.read()) != -1) {
                        for (Map.Entry<Predicate<Integer>, BufferedWriter> entry : writers.entrySet()) {
                            if (entry.getKey().test(character)) {
                                entry.getValue().write(character);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("File operation failed", e);
            } finally {
                // Safely close resources
                for (BufferedWriter writer : writers.values()) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    // --- 3. Factory ---
    public static class SymbolsDistributorFactory {
        public SymbolsDistributor getInstance() {
            return new SymbolsDistributorImpl();
        }
    }

    // --- 4. Standalone Runner (Main) without parameters ---
    public static void main(String[] args) {
        System.out.println("=== Starting SymbolsDistributor ===");

        try {
            // Create a temporary input file for testing
            File inputFile = new File("sample_input.txt");
            try (FileWriter writer = new FileWriter(inputFile)) {
                writer.write("Java 21 & 2026 Programming - Hello World!");
            }
            System.out.println("Input file created: " + inputFile.getName() + " (Content: 'Java 21 & 2026 Programming - Hello World!')");

            // Define output files
            File lettersFile = new File("out_letters.txt");
            File digitsFile = new File("out_digits.txt");
            File punctuationFile = new File("out_punctuation.txt");

            // Map filtering predicates to output files
            Map<Predicate<Integer>, File> outputMapping = new HashMap<>();
            outputMapping.put(Character::isLetter, lettersFile);
            outputMapping.put(Character::isDigit, digitsFile);
            outputMapping.put(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c), punctuationFile);

            // Instantiate and run via Factory
            SymbolsDistributorFactory factory = new SymbolsDistributorFactory();
            SymbolsDistributor distributor = factory.getInstance();

            distributor.distribute(inputFile, outputMapping);
            System.out.println("\nCharacters successfully distributed into files!\n");

            // Print results
            printFileContent("Letters file", lettersFile);
            printFileContent("Digits file", digitsFile);
            printFileContent("Punctuation file", punctuationFile);

        } catch (Exception e) {
            System.err.println("An error occurred during execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printFileContent(String label, File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            System.out.println(label + " (" + file.getName() + " -> " + content + ")");
        } catch (IOException e) {
            System.out.println(label + " cannot be read.");
        }
    }
}
/*
This Java program reads the content of an arbitrary input file character by character, 
then sorts and distributes the characters into separate output files based on user-defined conditions (Predicate).
Key features and functionality:
Condition-based filtering: 
You can specify which character group (e.g., only letters, only numbers, or only punctuation marks) goes into which target file.
Factory pattern: 
The SymbolsDistributorFactory provides clean instantiation to access the core logic.
Standalone executability: 
Thanks to the main method, it creates a test file, performs the distribution, and prints the results to the console.
*/

