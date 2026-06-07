package per.nonobeam.common.account;

/**
 * Builds, parses, and validates CoA path strings. This is the only gate against invalid paths
 * reaching the DB.
 */
public final class CoaPathParser {

  private CoaPathParser() {}

  // ─── builders ────────────────────────────────────────────────────────────────

  public static String bankPath(String sub, AccountState state) {
    validate("bank", sub, "shared", "prop");
    validate("state", state.name(), "MAIN", "CLEARING");
    return "bank:" + sub + ":" + state.name().toLowerCase();
  }

  public static String walletControlPath(AccountState state) {
    validate("state", state.name(), "MAIN", "RESERVED", "CLEARING");
    return "wallet:control:" + state.name().toLowerCase();
  }

  public static String walletPath(String customerId, String walletId, AccountState state) {
    requireNonBlank("customerId", customerId);
    requireNonBlank("walletId", walletId);
    validate("state", state.name(), "MAIN", "RESERVED", "CLEARING");
    // Reject customer IDs from GL-only concepts (no validation needed here for external/bank since
    // those use separate builders, but guard against control segment)
    if ("control".equals(customerId)) {
      throw new IllegalArgumentException("customerId must not be 'control' for SUB wallet paths");
    }
    return "wallet:" + customerId + ":" + walletId + ":" + state.name().toLowerCase();
  }

  public static String receivablePath(String provider) {
    requireNonBlank("provider", provider);
    return "receivable:counterparty:" + provider.toLowerCase();
  }

  public static String receivableReturnPath(String provider) {
    requireNonBlank("provider", provider);
    return "receivable:counterparty:" + provider.toLowerCase() + ":return";
  }

  public static String payablePath(String provider) {
    requireNonBlank("provider", provider);
    return "payable:counterparty:" + provider.toLowerCase();
  }

  public static String offsetPath(String customerId) {
    requireNonBlank("customerId", customerId);
    return "offset:segregated:" + customerId + ":paid_fee";
  }

  public static String revenuePath(String customerId) {
    requireNonBlank("customerId", customerId);
    return "revenue:segregated:" + customerId + ":fee";
  }

  public static String externalPath(String provider) {
    requireNonBlank("provider", provider);
    return "external:counterparty:" + provider.toLowerCase();
  }

  public static String loyaltyExpensePath() {
    return "expense:system:loyalty";
  }

  public static String ptsControlPath() {
    return "pts:control:reserve";
  }

  public static String ptsUserPath(String userId) {
    requireNonBlank("userId", userId);
    return "pts:user:" + userId;
  }

  // ─── parser ──────────────────────────────────────────────────────────────────

  public static CoaPath parse(String path) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException("CoA path must not be blank");
    }
    String[] parts = path.split(":", -1);
    if (parts.length < 2) {
      throw new IllegalArgumentException("Invalid CoA path: " + path);
    }

    String head = parts[0];
    return switch (head) {
      case "bank" -> {
        validateMinSegments(path, parts, 3);
        yield new CoaPath(AccountType.BANK, LedgerType.GL, path, tail(parts, 1));
      }
      case "wallet" -> {
        validateMinSegments(path, parts, 3);
        boolean isControl = "control".equals(parts[1]);
        LedgerType ledger = isControl ? LedgerType.GL : LedgerType.SUB;
        yield new CoaPath(AccountType.WALLET, ledger, path, tail(parts, 1));
      }
      case "receivable" -> {
        validateMinSegments(path, parts, 3);
        yield new CoaPath(AccountType.RECEIVABLE, LedgerType.GL, path, tail(parts, 1));
      }
      case "payable" -> {
        validateMinSegments(path, parts, 3);
        yield new CoaPath(AccountType.PAYABLE, LedgerType.GL, path, tail(parts, 1));
      }
      case "offset" -> {
        validateMinSegments(path, parts, 4);
        yield new CoaPath(AccountType.OFFSET, LedgerType.GL, path, tail(parts, 1));
      }
      case "revenue" -> {
        validateMinSegments(path, parts, 4);
        yield new CoaPath(AccountType.REVENUE, LedgerType.GL, path, tail(parts, 1));
      }
      case "external" -> {
        validateMinSegments(path, parts, 3);
        yield new CoaPath(AccountType.EXTERNAL, LedgerType.GL, path, tail(parts, 1));
      }
      case "expense" -> {
        validateMinSegments(path, parts, 3);
        yield new CoaPath(AccountType.EXPENSE, LedgerType.GL, path, tail(parts, 1));
      }
      case "pts" -> {
        validateMinSegments(path, parts, 3);
        yield new CoaPath(AccountType.PTS, LedgerType.GL, path, tail(parts, 1));
      }
      default ->
          throw new IllegalArgumentException("Unknown CoA path type: " + head + " in " + path);
    };
  }

  /** Returns the ledger type for a given CoA path without full parsing. */
  public static LedgerType ledgerOf(String path) {
    return parse(path).ledger();
  }

  /** True if this path represents a wallet:control:* GL aggregate account. */
  public static boolean isWalletControl(String path) {
    return path != null && path.startsWith("wallet:control:");
  }

  // ─── helpers ─────────────────────────────────────────────────────────────────

  private static void validate(String field, String value, String... allowed) {
    for (String a : allowed) {
      if (a.equals(value)) {
        return;
      }
    }
    throw new IllegalArgumentException("Invalid value for " + field + ": " + value);
  }

  private static void requireNonBlank(String field, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }

  private static void validateMinSegments(String path, String[] parts, int min) {
    if (parts.length < min) {
      throw new IllegalArgumentException(
          "CoA path too short (expected >= " + min + " segments): " + path);
    }
  }

  private static String[] tail(String[] parts, int from) {
    String[] result = new String[parts.length - from];
    System.arraycopy(parts, from, result, 0, result.length);
    return result;
  }
}
