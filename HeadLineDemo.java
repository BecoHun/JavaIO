import java.time.LocalDateTime;
import java.time.Month;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeadLineDemo {

  // ======================================================================
  //   1) The ReferenceFormat interface (types, values, patterns)
  // ======================================================================

  interface ReferenceFormat {

    enum Type {
      COMPANY(String.class),
      PERSON_NAME(String.class, String.class),
      INTEGER(Integer.class),
      PERCENT(Double.class),
      DOUBLE(Double.class),
      DATE_TIME(LocalDateTime.class);

      private final Class<?>[] validParameters;

      Type(Class<?>... validParameters) {
        this.validParameters = validParameters;
      }

      private boolean isValid(Object[] parameters) {
        if (validParameters.length != parameters.length) {
          return false;
        }
        for (int i = 0; i < validParameters.length; i++) {
          if (!validParameters[i].isAssignableFrom(parameters[i].getClass())) {
            return false;
          }
        }
        return true;
      }
    }

    record ReferenceValue(Type type, Object... parameters) {
      public ReferenceValue {
        if (!type.isValid(parameters)) {
          throw new IllegalArgumentException(
              type + " does not accept " + Arrays.toString(parameters) + " as input");
        }
      }
    }

    /** Selects the appropriate pattern based on the type. */
    default String patternFor(ReferenceValue value) {
      return switch (value.type()) {
        case COMPANY -> companyPattern();
        case PERSON_NAME -> personNamePattern();
        case INTEGER -> integerPattern();
        case DOUBLE -> doublePattern();
        case PERCENT -> percentPattern();
        case DATE_TIME -> dateTimePattern();
      };
    }

    /** Formats the value's parameters with the selected pattern. */
    default String format(ReferenceValue value) {
      // Locale.US: decimal point, group separator, and month names do not depend on the machine's locale
      return String.format(Locale.US, patternFor(value), value.parameters());
    }

    String companyPattern();
    String personNamePattern();
    String integerPattern();
    String doublePattern();
    String percentPattern();
    String dateTimePattern();
  }

  // ======================================================================
  //   2) The two predefined formats
  // ======================================================================

  static final class ReferenceFormatFactory {

    private static final ReferenceFormat SIMPLE = new PatternFormat(
        "\"%s\"",                     // company: "Edison"
        "%s %S",                      // first name + LAST NAME
        "%,d",                        // group separator
        "%,.2f",                      // group separator, 2 decimals
        "%+.1f%%",                    // sign, 1 decimal, percent sign
        "on %1$tF at %1$tk o'clock"   // yyyy-MM-dd, hour (0-23) without padding
    );

    private static final ReferenceFormat FORMAL = new PatternFormat(
        "%S LLC",                     // uppercase + LLC
        "%2$s, %1$s",                 // last name, first name
        "% d",                        // placeholder reserved for plus sign
        "%+.2E",                      // scientific notation, sign, 2 decimals
        "%05.2f%%",                   // 2 decimals, width of 5 with leading zeros (05.10%)
        "at %1$tH:%1$tM of %1$te %1$tb %1$tY"   // at 15:45 of 13 Apr 2020
    );

    private ReferenceFormatFactory() {}

    static ReferenceFormat simpleFormat() {
      return SIMPLE;
    }

    static ReferenceFormat formalFormat() {
      return FORMAL;
    }

    private record PatternFormat(String company, String personName, String integer,
                               String doubleValue, String percent, String dateTime)
        implements ReferenceFormat {

      @Override public String companyPattern() { return company; }
      @Override public String personNamePattern() { return personName; }
      @Override public String integerPattern() { return integer; }
      @Override public String doublePattern() { return doubleValue; }
      @Override public String percentPattern() { return percent; }
      @Override public String dateTimePattern() { return dateTime; }
    }
  }

  // ======================================================================
  //   3) The template engine
  // ======================================================================

  record ReferencedText(String text, Map<String, ReferenceFormat.ReferenceValue> references) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");

    String format(ReferenceFormat format) {
      return format(format, line -> { });
    }

    /** Same as format, but sends a message about each replacement to the trace. */
    String format(ReferenceFormat format, Consumer<String> trace) {
      return PLACEHOLDER.matcher(text).replaceAll(match -> {
        String name = match.group(1);
        ReferenceFormat.ReferenceValue value = references.get(name);
        if (value == null) {
          trace.accept(String.format("   ${%s} -> no value provided, remains untouched", name));
          return Matcher.quoteReplacement(match.group());
        }
        String replacement = format.format(value);
        trace.accept(String.format("   ${%s}%n       type: %s | parameters: %s%n"
                + "       pattern: %s%n       result: [%s]",
            name, value.type(), Arrays.toString(value.parameters()),
            format.patternFor(value), replacement));
        // quoteReplacement: prevent special interpretation of '$' and '\' characters
        return Matcher.quoteReplacement(replacement);
      });
    }
  }

  // ======================================================================
  //   4) Demo
  // ======================================================================

  private static final String HEADLINE = """
      ${CEO} is a CEO of ${company}. ${company} stock price was changed by ${stock change} ${change date},
      it has around ${employees number} employees. Budget change estimation for the end of this year is ${estimation}$.
      """;

  private static int passed = 0;
  private static int failed = 0;

  public static void main(String[] args) {
    banner("Template Engine Demo (Java " + System.getProperty("java.version") + ")");
    System.out.println("""
        This program replaces placeholders like ${name} with formatted values.
        Each placeholder is associated with a type (company, name, integer, ...), and a pattern
        belongs to the selected format (simple / formal) for that type.
        Every step is shown below, and finally, the result is compared with the expected text.
        """);

    System.out.println("Template:");
    System.out.println(indent(HEADLINE));

    runCase(1, "simple", ReferenceFormatFactory.simpleFormat(),
        refs("Oolong", "Tusk", "Edison", -13.46,
            LocalDateTime.of(2020, Month.APRIL, 13, 15, 45), 26, -4750000d),
        """
            Oolong TUSK is a CEO of "Edison". "Edison" stock price was changed by -13.5% on 2020-04-13 at 15 o'clock,
            it has around 26 employees. Budget change estimation for the end of this year is -4,750,000.00$.
            """);

    runCase(2, "simple", ReferenceFormatFactory.simpleFormat(),
        refs("John", "Cool", "Bar", +2.147,
            LocalDateTime.of(2020, Month.APRIL, 13, 7, 0), 24763, 5.725d),
        """
            John COOL is a CEO of "Bar". "Bar" stock price was changed by +2.1% on 2020-04-13 at 7 o'clock,
            it has around 24,763 employees. Budget change estimation for the end of this year is 5.73$.
            """);

    runCase(3, "formal", ReferenceFormatFactory.formalFormat(),
        refs("Brian", "August", "Queen", -13.46,
            LocalDateTime.of(2020, Month.APRIL, 13, 15, 45), 26, -4750000d),
        """
            August, Brian is a CEO of QUEEN LLC. QUEEN LLC stock price was changed by -13.46% at 15:45 of 13 Apr 2020,
            it has around  26 employees. Budget change estimation for the end of this year is -4.75E+06$.
            """);

    runCase(4, "formal", ReferenceFormatFactory.formalFormat(),
        refs("Key", "Space", "Enter", +5.1,
            LocalDateTime.of(2020, Month.JANUARY, 1, 15, 45), 1226, 1d),
        """
            Space, Key is a CEO of ENTER LLC. ENTER LLC stock price was changed by 05.10% at 15:45 of 1 Jan 2020,
            it has around  1226 employees. Budget change estimation for the end of this year is +1.00E+00$.
            """);

    bonusCase();

    banner("Summary");
    System.out.printf("Passed: %d, failed: %d%n", passed, failed);
    if (failed > 0) {
      System.exit(1);
    }
  }

  private static void runCase(int number, String formatName, ReferenceFormat format,
                              Map<String, ReferenceFormat.ReferenceValue> references,
                              String expected) {
    banner(number + ". case: " + formatName + " format");
    ReferencedText text = new ReferencedText(HEADLINE, references);

    System.out.println("Step-by-step (in the order of the template):");
    String actual = text.format(format, System.out::println);

    System.out.println();
    System.out.println("Final result:");
    System.out.println(indent(actual));

    if (expected.equals(actual)) {
      passed++;
      System.out.println("VERIFICATION: OK, matches the expected text.");
    } else {
      failed++;
      System.out.println("VERIFICATION: ERROR, differs from expected!");
      System.out.println("Expected:");
      System.out.println(indent(expected));
    }
    System.out.println();
  }

  /** A sentence containing the same placeholder multiple times and an unknown one. */
  private static void bonusCase() {
    banner("Bonus: multiple occurrences and unknown placeholder");
    String template = "${c} again ${c}, but ${unknown} is unknown. Price: ${p}.";
    ReferencedText text = new ReferencedText(template, Map.of(
        "c", new ReferenceFormat.ReferenceValue(ReferenceFormat.Type.COMPANY, "Macrohard"),
        "p", new ReferenceFormat.ReferenceValue(ReferenceFormat.Type.DOUBLE, 450000.50)));

    System.out.println("Template: " + template);
    String result = text.format(ReferenceFormatFactory.simpleFormat(), System.out::println);
    System.out.println("Result: " + result);
    System.out.println();

    System.out.println("Testing invalid input (ReferenceValue validates parameters):");
    try {
      new ReferenceFormat.ReferenceValue(ReferenceFormat.Type.INTEGER, "not a number");
    } catch (IllegalArgumentException e) {
      System.out.println("  Exception as expected: " + e.getMessage());
    }
    System.out.println();
  }

  private static Map<String, ReferenceFormat.ReferenceValue> refs(
      String ceoName, String ceoLastName, String company, Double stockChange,
      LocalDateTime changeDate, Integer employeesNumber, Double estimation) {
    ReferenceFormat.Type t;
    return Map.of(
        "CEO", new ReferenceFormat.ReferenceValue(ReferenceFormat.Type.PERSON_NAME, ceoName, ceoLastName),
        "company", new ReferenceFormat.ReferenceValue(ReferenceFormat.Type.COMPANY, company),
        "stock change", new ReferenceFormat.ReferenceValue(ReferenceFormat.Type.PERCENT, stockChange),
        "change date", new ReferenceFormat.ReferenceValue(ReferenceFormat.Type.DATE_TIME, changeDate),
        "employees number", new ReferenceFormat.ReferenceValue(ReferenceFormat.Type.INTEGER, employeesNumber),
        "estimation", new ReferenceFormat.ReferenceValue(ReferenceFormat.Type.DOUBLE, estimation)
    );
  }

  private static void banner(String title) {
    System.out.println("=".repeat(78));
    System.out.println(title);
    System.out.println("=".repeat(78));
  }

  private static String indent(String text) {
    return text.stripTrailing().lines().map(l -> "    | " + l).reduce((a, b) -> a + "\n" + b).orElse("");
  }
}
/*
HeadLineDemo is a robust, configurable template engine written in modern Java that replaces placeholders (${...}) in text templates with strictly validated, formatted data.
Key Features:
Type safety: 
The ReferenceValue record (Java 14+) validates at instantiation whether the number and type of input parameters are correct (e.g., it rejects text instead of an integer).
Flexible formats (SIMPLE / FORMAL): 
Provides predefined style profiles via ReferenceFormatFactory. 
This allows formatting (e.g., company names, dates, percentages, floating-point numbers) to be completely decoupled from raw data.
Detailed tracing: 
Logs each element step-by-step during substitutions, showing the type, the pattern used, and the result.
Intelligent error handling: 
Leaves missing placeholders untouched, while immediately throwing an IllegalArgumentException for invalid parameters.
*/

