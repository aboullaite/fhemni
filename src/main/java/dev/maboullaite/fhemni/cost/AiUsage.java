package dev.maboullaite.fhemni.cost;

public record AiUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer cachedTokens,
        Integer thoughtTokens,
        Integer toolUseTokens,
        Integer groundingQueries) {

    public static AiUsage empty() {
        return new AiUsage(null, null, null, null, null, null);
    }

    public AiUsage plus(AiUsage other) {
        if (other == null) {
            return this;
        }
        return new AiUsage(
                sum(inputTokens, other.inputTokens),
                sum(outputTokens, other.outputTokens),
                sum(cachedTokens, other.cachedTokens),
                sum(thoughtTokens, other.thoughtTokens),
                sum(toolUseTokens, other.toolUseTokens),
                sum(groundingQueries, other.groundingQueries));
    }

    private static Integer sum(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return (left == null ? 0 : left) + (right == null ? 0 : right);
    }
}
