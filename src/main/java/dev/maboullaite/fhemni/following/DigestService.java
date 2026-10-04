package dev.maboullaite.fhemni.following;

import java.time.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.maboullaite.fhemni.government.GovernmentViews.*;

/** Consent and outbox lifecycle; no production transport or scheduler is installed. */
@Service
public class DigestService {
    private final DigestRepository repository;private final UpdateFeedService feed;private final DigestEmailSender sender;
    private final boolean enabled;private final String key,base;
    public DigestService(DigestRepository repository,UpdateFeedService feed,DigestEmailSender sender,@Value("${fhemni.digest.enabled:false}")boolean enabled,@Value("${fhemni.digest.signing-key:}")String key,@Value("${fhemni.digest.base-url:}")String base){this.repository=repository;this.feed=feed;this.sender=sender;this.enabled=enabled;this.key=key;this.base=base.replaceAll("/+$","");}
    public boolean capable(){try{var uri=URI.create(base);return enabled&&key.length()>=32&&"https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getQuery()==null&&uri.getFragment()==null&&sender.configured();}catch(IllegalArgumentException e){return false;}}
    public DigestPreferences preferences(UUID user){var consent=repository.consent(user);String email=repository.verifiedEmail(user);boolean eligible=email!=null;return new DigestPreferences(consent.enabled(),consent.locale(),capable()&&eligible,capable()?eligible?null:"VERIFIED_EMAIL_REQUIRED":"UNAVAILABLE");}
    @Transactional public DigestPreferences update(UUID user,boolean subscribe,String locale){repository.lock(user);if(locale==null||!Set.of("ar","fr","en").contains(locale))throw new IllegalArgumentException("Choose a supported language.");String email=repository.verifiedEmail(user);if(subscribe&&(!capable()||email==null))throw new IllegalArgumentException("Digest delivery requires an available transport and verified email.");var current=repository.consent(user);if(current.enabled()!=subscribe||!current.locale().equals(locale)||!Objects.equals(current.consentEmail(),email))repository.save(user,subscribe,locale,email);return preferences(user);}
    @Transactional public void reserve(UUID user,LocalDate start){if(start.getDayOfWeek()!=DayOfWeek.MONDAY||start.plusWeeks(1).isAfter(LocalDate.now(ZoneOffset.UTC)))throw new IllegalArgumentException("Choose a complete UTC week.");repository.lock(user);var consent=repository.consent(user);if(!deliverable(user,consent)||updates(user,start).isEmpty())return;repository.reserve(user,start,consent.consentVersion());}
    private boolean deliverable(UUID user,DigestRepository.Consent consent){return capable()&&consent.enabled()&&consent.consentEmail()!=null&&consent.consentEmail().equals(repository.verifiedEmail(user));}
    private List<PublishedUpdate> updates(UUID user,LocalDate start){return feed.between(user,start.atStartOfDay().toInstant(ZoneOffset.UTC),start.plusWeeks(1).atStartOfDay().toInstant(ZoneOffset.UTC));}
    @Transactional public void deliver(DigestRepository.Claim claim){
        repository.lock(claim.userId());var consent=repository.consent(claim.userId());
        if(!deliverable(claim.userId(),consent)||!Objects.equals(consent.consentVersion(),claim.consentVersion())){repository.finish(claim,"CANCELLED");return;}
        var rows=updates(claim.userId(),claim.periodStart());if(rows.isEmpty()){repository.finish(claim,"CANCELLED");return;}
        String locale=consent.locale(),subject=switch(locale){case "fr"->"Votre suivi Fhemni — cette semaine";case "en"->"Your Fhemni updates this week";default->"مستجدات المتابعة ديالك ففهّمني";};
        String view=switch(locale){case "fr"->"Voir toutes les mises à jour";case "en"->"View all updates";default->"شوف جميع المستجدات";};
        var html=new StringBuilder("<!doctype html><html lang=\"").append(locale).append("\" dir=\"").append(locale.equals("ar")?"rtl":"ltr").append("\"><body><h1>").append(escape(subject)).append("</h1><ul>");var text=new StringBuilder(subject).append('\n');
        for(var row:rows.stream().limit(10).toList()){String title=localized(row.title(),locale),path=switch(row.subjectType()){case "COMMITMENT"->"/government/commitments/"+UUID.fromString(row.subjectId());case "MEASURE"->"/government/measures/"+UUID.fromString(row.subjectId());default->"/government";};String url=base+path;text.append(title).append(" — ").append(url).append('\n');html.append("<li><a href=\"").append(escape(url)).append("\">").append(escape(title)).append("</a></li>");}
        html.append("</ul><p><a href=\"").append(escape(base+"/following")).append("\">").append(escape(view)).append("</a></p></body></html>");text.append(view).append(": ").append(base).append("/following");
        if(!repository.beginSend(claim))return;
        try{sender.send(consent.consentEmail(),subject,text.toString(),html.toString());repository.finish(claim,"SENT");}catch(DigestEmailSender.Rejected failure){repository.finish(claim,"FAILED");}catch(RuntimeException ambiguous){repository.finish(claim,"UNKNOWN");}
    }
    /** Signing contract tested locally, deliberately not exposed as a public endpoint. */
    public String unsubscribeToken(UUID user,Instant expiry){var consent=repository.consent(user);String data="digest-unsubscribe:"+user+":"+consent.consentVersion()+":"+expiry.getEpochSecond();return Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8))+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(data));}
    private byte[] sign(String data){if(key.length()<32)throw new IllegalStateException("Digest signing is not configured.");try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));}catch(java.security.GeneralSecurityException error){throw new IllegalStateException(error);}}
    private String[] validateToken(String token){try{if(token==null||token.length()>1024)throw new IllegalArgumentException();var parts=token.split("\\.",-1);if(parts.length!=2)throw new IllegalArgumentException();String data=new String(Base64.getUrlDecoder().decode(parts[0]),StandardCharsets.UTF_8);if(!MessageDigest.isEqual(sign(data),Base64.getUrlDecoder().decode(parts[1])))throw new IllegalArgumentException();var fields=data.split(":",-1);if(fields.length!=4||!fields[0].equals("digest-unsubscribe")||Long.parseLong(fields[3])<=Instant.now().getEpochSecond())throw new IllegalArgumentException();UUID.fromString(fields[1]);UUID.fromString(fields[2]);return fields;}catch(RuntimeException error){throw new IllegalArgumentException("This unsubscribe link is invalid or expired.");}}
    @Transactional public void unsubscribe(String token){var fields=validateToken(token);var user=UUID.fromString(fields[1]);repository.lock(user);var consent=repository.consent(user);if(!Objects.equals(consent.consentVersion(),UUID.fromString(fields[2])))throw new IllegalArgumentException("This consent has changed. Use the latest email or your account settings.");if(consent.enabled())repository.save(user,false,consent.locale(),consent.consentEmail());}
    static String localized(LocalText text,String locale){return switch(locale){case "fr"->text.fr();case "en"->text.en();default->text.ar();};}
    static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
