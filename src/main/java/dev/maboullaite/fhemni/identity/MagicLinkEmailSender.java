package dev.maboullaite.fhemni.identity;

public interface MagicLinkEmailSender {

    void send(String from, String to, String subject, String text, String html);
}
