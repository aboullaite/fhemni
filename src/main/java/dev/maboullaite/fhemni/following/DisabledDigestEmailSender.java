package dev.maboullaite.fhemni.following;
import org.springframework.stereotype.Component;
/** No external delivery is wired in this local-review branch. Tests substitute a fake sender. */
@Component
public class DisabledDigestEmailSender implements DigestEmailSender {
    public boolean configured(){return false;}
    public void send(String to,String subject,String text,String html){throw new Rejected();}
}
