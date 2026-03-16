package per.nonobeam.web.model.deposit;

public record DepositResponse(
    String transactionId, String sessionId, String redirectUrl, String status) {}
