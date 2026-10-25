package dev.maboullaite.fhemni.memberanalysis;
public interface VideoEligibilityGateway {
    record Eligibility(boolean available,Long durationSeconds,boolean live,String reason) {}
    boolean configured();
    Eligibility inspect(String videoId);
}
