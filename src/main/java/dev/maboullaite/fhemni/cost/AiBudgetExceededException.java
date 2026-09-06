package dev.maboullaite.fhemni.cost;

public class AiBudgetExceededException extends IllegalStateException {

    private final String code;

    public AiBudgetExceededException(String message) {
        this("AI_BUDGET_EXCEEDED", message);
    }

    public AiBudgetExceededException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
