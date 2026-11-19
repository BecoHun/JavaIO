import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class RollingZipperDemo {

  private static final long SIZE_THRESHOLD = 1500;
  private static final long AMOUNT_THRESHOLD = 3;

  public static void main(String[] args) throws IOException {
    System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
    Path workDir = Files.createTempDirectory("rolling-zipper-demo");
    try {
      Path inputDir = Files.createDirectory(workDir.resolve("input"));
      List<File> inputs = createSampleFiles(inputDir);

      banner("Rolling Zipper Demo");
      System.out.println("Input files are archived sequentially; when the policy says it is");
      System.out.println("time to \"roll\", we close the current ZIP and switch to the next output.");
      printInputs(inputs);

      // 1) Size-based policy
      runScenario("Case 1: size-based policy (threshold: " + SIZE_THRESHOLD + " bytes compressed size)",
          "size", workDir, inputs, RollingPolicyFactory.sizePolicy(SIZE_THRESHOLD),
          Check.SIZE, SIZE_THRESHOLD);

      // 2) Count-based policy
      runScenario("Case 2: count-based policy (threshold: " + AMOUNT_THRESHOLD + " files / archive)",
          "amount", workDir, inputs, RollingPolicyFactory.amountPolicy(AMOUNT_THRESHOLD),
          Check.AMOUNT, AMOUNT_THRESHOLD);

      // 3) Size-based policy with reordered input files
      List<File> reordered = new ArrayList<>(inputs);
      File big = reordered.remove(4); // the large, highly compressible file
      reordered.add(0, big);
      swapLastTwo(reordered);
      runScenario("Case 3: size-based policy with reordered files (order matters!)",
          "reordered", workDir, reordered, RollingPolicyFactory.sizePolicy(SIZE_THRESHOLD),
          Check.SIZE, SIZE_THRESHOLD);

      banner("Done - cleaning up temporary files");
    } finally {
      deleteRecursively(workDir);
    }
  }

  private static void swapLastTwo(List<File> list) {
    int n = list.size();
    File tmp = list.get(n - 1);
    list.set(n - 1, list.get(n - 2));
    list.set(n - 2, tmp);
  }

  // ---------------------------------------------------------------- scenario

  private enum Check { SIZE, AMOUNT }

  private static void runScenario(String title, String prefix, Path workDir, List<File> inputs,
      RollingPolicy policy, Check check, long threshold) throws IOException {
    banner(title);
    Path outDir = Files.createDirectories(workDir.resolve("out-" + prefix));
    FileOutputs outputs = new FileOutputs(outDir, prefix);

    new RollingZipper().zip(inputs, policy, outputs);

    List<List<EntryInfo>> archives = new ArrayList<>();
    for (int i = 0; i < outputs.paths.size(); i++) {
      Path p = outputs.paths.get(i);
      List<EntryInfo> entries = readArchive(p);
      archives.add(entries);

      long sum = entries.stream().mapToLong(EntryInfo::compressedSize).sum();
      System.out.printf("%n  %s  (%d bytes on disk, %d files, sum of compressed sizes: %d)%n",
          p.getFileName(), Files.size(p), entries.size(), sum);
      for (EntryInfo e : entries) {
        System.out.printf("      - %-14s original: %5d B   compressed: %5d B%n",
            e.name(), e.originalSize(), e.compressedSize());
      }
    }

    System.out.println();
    System.out.println("  Verification:");
    List<String> expectedNames = inputs.stream().map(File::getName).toList();
    List<String> actualNames = archives.stream().flatMap(List::stream).map(EntryInfo::name).toList();
    report("all files are present, in original order, with original names",
        expectedNames.equals(actualNames));

    boolean contentOk = true;
    int idx = 0;
    for (List<EntryInfo> archive : archives) {
      for (EntryInfo e : archive) {
        contentOk &= Arrays.equals(e.content(), Files.readAllBytes(inputs.get(idx++).toPath()));
      }
    }
    report("content matches original byte for byte", contentOk);

    if (check == Check.AMOUNT) {
      boolean ok = archives.stream().allMatch(a -> a.size() <= threshold);
      report("no archive contains more than " + threshold + " files", ok);
    } else {
      boolean ok = true;
      for (int i = 0; i < archives.size(); i++) {
        List<EntryInfo> a = archives.get(i);
        long sum = a.stream().mapToLong(EntryInfo::compressedSize).sum();
        long withoutLast = sum - a.get(a.size() - 1).compressedSize();
        boolean last = i == archives.size() - 1;
        // After rolling, the sum reached the threshold (except for the last archive),
        // but was below threshold without the last file -> exceeded by at most one file.
        ok &= withoutLast < threshold && (last || sum >= threshold);
      }
      report("every closed archive reached the threshold, exceeding it only with the last file", ok);
    }
    report("no empty archives", archives.stream().noneMatch(List::isEmpty));
  }

  private static void report(String text, boolean ok) {
    System.out.printf("    [%s] %s%n", ok ? " OK " : "FAIL", text);
  }

  // ---------------------------------------------------------------- sample files

  private static List<File> createSampleFiles(Path dir) throws IOException {
    // name, type (T = compressible text, R = random bytes, barely compressible), size
    Object[][] spec = {
        {"file-01.txt", 'T', 4000}, {"file-02.bin", 'R', 600}, {"file-03.txt", 'T', 2500},
        {"file-04.bin", 'R', 900}, {"file-05.txt", 'T', 8000}, {"file-06.bin", 'R', 400},
        {"file-07.txt", 'T', 1200}, {"file-08.bin", 'R', 1100}, {"file-09.txt", 'T', 3000},
        {"file-10.bin", 'R', 300},
    };
    List<File> files = new ArrayList<>();
    Random random = new Random(42);
    for (Object[] s : spec) {
      String name = (String) s[0];
      int size = (int) s[2];
      byte[] data;
      if ((char) s[1] == 'T') {
        StringBuilder sb = new StringBuilder();
        int line = 1;
        while (sb.length() < size) {
          sb.append("Line ").append(line++).append(": the quick brown fox jumps over the lazy dog.\n");
        }
        data = Arrays.copyOf(sb.toString().getBytes(StandardCharsets.UTF_8), size);
      } else {
        data = new byte[size];
        random.nextBytes(data);
      }
      Path p = dir.resolve(name);
      Files.write(p, data);
      files.add(p.toFile());
    }
    files.sort(Comparator.comparing(File::getName));
    return files;
  }

  private static void printInputs(List<File> inputs) {
    System.out.println();
    System.out.println("  Input files (in order):");
    for (File f : inputs) {
      String kind = f.getName().endsWith(".txt") ? "text, compressible" : "random, barely compressible";
      System.out.printf("      %-14s %5d B   (%s)%n", f.getName(), f.length(), kind);
    }
  }

  // ---------------------------------------------------------------- ZIP reading

  private record EntryInfo(String name, long originalSize, long compressedSize, byte[] content) {}

  private static List<EntryInfo> readArchive(Path zip) throws IOException {
    List<EntryInfo> result = new ArrayList<>();
    try (ZipFile zf = new ZipFile(zip.toFile())) {
      Enumeration<? extends ZipEntry> en = zf.entries();
      while (en.hasMoreElements()) {
        ZipEntry e = en.nextElement();
        byte[] content = zf.getInputStream(e).readAllBytes();
        result.add(new EntryInfo(e.getName(), e.getSize(), e.getCompressedSize(), content));
      }
    }
    return result;
  }

  // ---------------------------------------------------------------- outputs

  /** Infinite iterator that opens a new ZIP file on each call (prefix-1.zip, prefix-2.zip, ...). */
  private static class FileOutputs implements Iterator<OutputStream> {
    private final Path dir;
    private final String prefix;
    final List<Path> paths = new ArrayList<>();

    FileOutputs(Path dir, String prefix) {
      this.dir = dir;
      this.prefix = prefix;
    }

    @Override
    public boolean hasNext() {
      return true;
    }

    @Override
    public OutputStream next() {
      Path p = dir.resolve(prefix + "-" + (paths.size() + 1) + ".zip");
      paths.add(p);
      try {
        return new BufferedOutputStream(Files.newOutputStream(p));
      } catch (IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    }
  }

  // ---------------------------------------------------------------- helpers

  private static void banner(String text) {
    String line = "=".repeat(Math.max(text.length() + 4, 60));
    System.out.println();
    System.out.println(line);
    System.out.println("  " + text);
    System.out.println(line);
  }

  private static void deleteRecursively(Path root) throws IOException {
    try (Stream<Path> walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    }
  }
}

// ==================================================================== solution

interface RollingPolicy {

  boolean readyToRoll(ZipEntry entry);
}

final class RollingPolicyFactory {

  private RollingPolicyFactory() {
    throw new UnsupportedOperationException();
  }

  /** Rolls when the sum of compressed sizes of files in the archive reaches or exceeds the threshold. */
  static RollingPolicy sizePolicy(long sizeThreshold) {
    return new RollingPolicy() {
      private long currentSize = 0;

      @Override
      public boolean readyToRoll(ZipEntry entry) {
        currentSize += entry.getCompressedSize();
        if (currentSize >= sizeThreshold) {
          currentSize = 0;
          return true;
        }
        return false;
      }
    };
  }

  /** Rolls when the number of files in the archive reaches the threshold. */
  static RollingPolicy amountPolicy(long amountThreshold) {
    return new RollingPolicy() {
      private long currentAmount = 0;

      @Override
      public boolean readyToRoll(ZipEntry entry) {
        currentAmount++;
        if (currentAmount >= amountThreshold) {
          currentAmount = 0;
          return true;
        }
        return false;
      }
    };
  }
}

class RollingZipper {

  public void zip(Iterable<File> inputFiles, RollingPolicy policy, Iterator<OutputStream> outputs)
      throws IOException {
    ZipOutputStream current = null;
    try {
      for (File file : inputFiles) {
        // Request an output only when there is something to write, avoiding empty archives.
        if (current == null) {
          current = new ZipOutputStream(outputs.next());
        }

        ZipEntry entry = new ZipEntry(file.getName());
        current.putNextEntry(entry);
        Files.copy(file.toPath(), current);
        current.closeEntry(); // compressed size of the entry becomes available here

        if (policy.readyToRoll(entry)) {
          current.close();
          current = null;
        }
      }
    } finally {
      if (current != null) {
        current.close();
      }
    }
  }
}

/*
The program demonstrates how a rolling ZIP archiver (RollingZipper) works via the executable RollingZipperDemo test harness. 
It packs files sequentially into ZIP archives and uses a rolling policy (RollingPolicy) to decide when to close the current ZIP and open a new one for subsequent files.
Archiver and policies (RollingZipper, RollingPolicy, RollingPolicyFactory): 
RollingZipper requests a new output stream (outputs.next()) only when there is a file to write, avoiding empty archives. 
After closing an entry (closeEntry()), it checks the policy: 
sizePolicy tracks the sum of compressed sizes (1500 B), while amountPolicy tracks the number of files in the archive (3 files).
Output handling (FileOutputs): 
Acts as an infinite iterator that opens a new numbered ZIP file (<prefix>-1.zip, <prefix>-2.zip, ...) on demand.
Demo and verification (RollingZipperDemo): 
Creates 10 sample files (compressible text and barely compressible random bytes) in a temporary directory, 
runs three scenarios (size-based, count-based, and size-based with reordered files), 
reads back the archives, verifies content and ordering integrity, and cleans up.
*/

