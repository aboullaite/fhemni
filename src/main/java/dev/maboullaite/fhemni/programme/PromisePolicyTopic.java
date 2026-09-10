package dev.maboullaite.fhemni.programme;

public record PromisePolicyTopic(
        String code,
        String broadCode,
        Relationship relationship) {

    public enum Relationship {
        DIRECT,
        RELATED
    }

    public record Assignment(String code, Relationship relationship) {
    }
}
