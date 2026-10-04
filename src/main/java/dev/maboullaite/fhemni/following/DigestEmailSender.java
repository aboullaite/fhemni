package dev.maboullaite.fhemni.following;
public interface DigestEmailSender {
    boolean configured();
    void send(String to,String subject,String text,String html);
    /** Definite provider rejection, not an ambiguous network outcome. */
    final class Rejected extends RuntimeException {}
}
