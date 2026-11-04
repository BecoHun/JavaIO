import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class LicenseReaderDemo {

    public static void main(String[] args) {
        System.out.println("=== LICENSE READER & PROCESSOR DEMO ===\n");

        try {
            // 1. Create root directory, subdirectories, and sample license files on disk
            File demoDir = new File("demo_licenses");
            setupDemoFiles(demoDir);

            File outputFile = new File("collected_licenses.txt");
            System.out.println("\n[INFO] Output file prepared: " + outputFile.getAbsolutePath());

            // 2. Instantiate and run the LicenseReader
            LicenseReader reader = new LicenseReader();
            System.out.println("[INFO] Starting license collection from: " + demoDir.getAbsolutePath() + "\n");
            
            reader.collectLicenses(demoDir, outputFile);

            System.out.println("\n[SUCCESS] Processing completed successfully!");
            System.out.println("--- Lines written to " + outputFile.getName() + " ---");
            
            if (outputFile.exists()) {
                List<String> lines = Files.readAllLines(outputFile.toPath());
                for (String line : lines) {
                    System.out.println("  > " + line);
                }
            } else {
                System.out.println("  (Output file not found.)");
            }

            // Optional cleanup (uncomment if you want to inspect the generated files on disk)
            // outputFile.delete();
            // deleteDir(demoDir);
            // System.out.println("\n[INFO] Temporary files and directories cleaned up.");

        } catch (Exception e) {
            System.err.println("\n[ERROR] An error occurred during execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void setupDemoFiles(File dir) throws IOException {
        // Ensure root directory exists (along with any parent directories)
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            System.out.println("[SETUP] Root directory created: " + dir.getPath() + " (" + created + ")");
        }
        
        // Sample license file 1 (in the root directory)
        File f1 = new File(dir, "license_java.txt");
        String content1 = """
                ---
                License : Enterprise Edition
                Issued by : Oracle
                Issued on : 2025-01-10
                Expires on : 2026-01-10
                ---
                Additional notes inside the file...
                """;
        Files.writeString(f1.toPath(), content1);
        System.out.println("[SETUP] File created: " + f1.getPath());

        // Create a subdirectory for recursive testing
        File subDir = new File(dir, "partner_licenses");
        if (!subDir.exists()) {
            subDir.mkdir();
            System.out.println("[SETUP] Subdirectory created: " + subDir.getPath());
        }

        // Sample license file 2 (inside the subdirectory)
        File f2 = new File(subDir, "license_intellij.txt");
        String content2 = """
                ---
                License : Ultimate
                Issued by : JetBrains
                Issued on : 2024-05-15
                ---
                """;
        Files.writeString(f2.toPath(), content2);
        System.out.println("[SETUP] File created in subdirectory: " + f2.getPath());
    }

    private static void deleteDir(File f) {
        File[] allFiles = f.listFiles();
        if (allFiles != null) {
            for (File child : allFiles) {
                deleteDir(child);
            }
        }
        f.delete();
    }
}

class LicenseReader {
    public void collectLicenses(File root, File outputFile) {
        if (root == null || outputFile == null) {
            throw new IllegalArgumentException("Root or outputFile cannot be null");
        }
        if (!root.exists() || !root.canRead() || (root.isDirectory() && !root.canExecute())) {
            throw new IllegalArgumentException("Root directory is invalid, unreadable, or not executable");
        }

        List<String> licenses = new ArrayList<>();
        collect(root, licenses);

        System.out.println("\n[IO] Collected " + licenses.size() + " valid licenses. Writing to: " + outputFile.getName());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, false))) {
            for (String license : licenses) {
                writer.write(license);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void collect(File file, List<String> licenses) {
        if (file.isDirectory()) {
            System.out.println("[DIR]  Traversing directory: " + file.getPath());
            File[] files = file.listFiles();
            if (files == null) {
                throw new IllegalArgumentException("Failed to list files in directory: " + file.getName());
            }
            for (File child : files) {
                collect(child, licenses);
            }
        } else {
            System.out.println("[FILE] Inspecting file: " + file.getPath());
            processLicense(file, licenses);
        }
    }

    private void processLicense(File file, List<String> licenses) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String firstLine = reader.readLine();
            if (!"---".equals(firstLine)) {
                System.out.println("       -> Skipped (not a license file header): " + file.getName());
                return;
            }
            String license = null;
            String issuer = null;
            String issuedOn = null;
            String expiresOn = "unlimited";
            boolean headerEnded = false;
            String line;

            while ((line = reader.readLine()) != null) {
                if ("---".equals(line)) {
                    headerEnded = true;
                    break;
                }
                String[] property = line.split(":", 2);
                if (property.length != 2) {
                    throw new IllegalArgumentException("Invalid property format in " + file.getName());
                }
                String key = property[0].trim();
                String value = property[1].trim();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("Empty value for key " + key);
                }
                switch (key) {
                    case "License" -> {
                        if (license != null) throw new IllegalArgumentException();
                        license = value;
                    }
                    case "Issued by" -> {
                        if (issuer != null) throw new IllegalArgumentException();
                        issuer = value;
                    }
                    case "Issued on" -> {
                        if (issuedOn != null) throw new IllegalArgumentException();
                        LocalDate.parse(value);
                        issuedOn = value;
                    }
                    case "Expires on" -> {
                        if (!"unlimited".equals(expiresOn)) throw new IllegalArgumentException();
                        LocalDate.parse(value);
                        expiresOn = value;
                    }
                    default -> throw new IllegalArgumentException("Unknown key: " + key);
                }
            }
            if (!headerEnded || license == null || issuer == null || issuedOn == null) {
                throw new IllegalArgumentException("Incomplete license header in " + file.getName());
            }
            
            String result = "License for " + file.getName() + " is " + license + " issued by " + issuer + " [" + issuedOn + " - " + expiresOn + "]";
            licenses.add(result);
            System.out.println("       -> Successfully processed: " + license);

        } catch (IOException | DateTimeParseException e) {
            throw new IllegalArgumentException("Error processing file: " + file.getName(), e);
        }
    }
}
/*
Main Tasks and Operation:
Setting up the sample environment (main / setupDemoFiles):
Creates a directory named demo_licenses on the disk, containing a subdirectory (partner_licenses).
Creates two sample license files (license_java.txt and license_intellij.txt) with a YAML-like header format (---).
Recursive traversal and reading (LicenseReader.collectLicenses & collect):
Validates the specified root directory (checks if it exists, is readable, and is executable).
Recursively traverses folders and subdirectories, mapping out all contained files.
Processing and validation of licenses (processLicense):
Opens the files and checks if their header starts with --- (if not, skips the file).
Reads the key-value pairs (License, Issued by, Issued on, Expires on), validates their formatting, and checks date validity using LocalDate.
If the header is valid, formats a standardized text string from it (e.g., License for file.txt is ... issued by ... [start - end]).
Output generation:
Writes all collected valid license lines into a target file named collected_licenses.txt, and prints the process steps and final result to the console.
*/

