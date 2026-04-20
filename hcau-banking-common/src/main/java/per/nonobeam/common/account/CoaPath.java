package per.nonobeam.common.account;

/**
 * Parsed representation of a CoA path string.
 *
 * <p>Format rules (from plan):
 *
 * <pre>
 * bank:{shared|prop}:{main|clearing}
 * wallet:control:{main|reserved|clearing}
 * wallet:{customer_id}:{wallet_id}:{main|reserved|clearing}
 * receivable:counterparty:{provider}
 * receivable:counterparty:{provider}:return
 * payable:counterparty:{provider}
 * offset:segregated:{customer_id}:paid_fee
 * revenue:segregated:{customer_id}:fee
 * external:counterparty:{provider}
 * </pre>
 */
public record CoaPath(AccountType accountType, LedgerType ledger, String raw, String[] segments) {

  /** True if this is the wallet:control:* GL aggregate account. */
  public boolean isWalletControl() {
    return accountType == AccountType.WALLET
        && segments.length >= 2
        && "control".equals(segments[0]);
  }

  /** Customer ID for wallet:{customer_id}:{wallet_id}:{state} paths. */
  public String customerId() {
    if (accountType != AccountType.WALLET || isWalletControl()) {
      return null;
    }
    return segments.length >= 1 ? segments[0] : null;
  }

  /** Wallet ID for wallet:{customer_id}:{wallet_id}:{state} paths. */
  public String walletId() {
    if (accountType != AccountType.WALLET || isWalletControl()) {
      return null;
    }
    return segments.length >= 2 ? segments[1] : null;
  }

  /** State segment (last segment) for accounts that carry a state. */
  public AccountState state() {
    if (segments.length == 0) {
      return null;
    }
    String last = segments[segments.length - 1];
    try {
      return AccountState.valueOf(last.toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** Provider name for receivable/payable/external paths. */
  public String provider() {
    if (accountType == AccountType.RECEIVABLE
        || accountType == AccountType.PAYABLE
        || accountType == AccountType.EXTERNAL) {
      return segments.length >= 2 ? segments[1] : null;
    }
    return null;
  }

  @Override
  public String toString() {
    return raw;
  }
}
