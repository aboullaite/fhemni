package dev.maboullaite.fhemni.memberanalysis;

import java.time.Duration;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Component
public class YouTubeEligibilityClient implements VideoEligibilityGateway {
    private final String key;private final RestClient client;
    public YouTubeEligibilityClient(@Value("${fhemni.member-analysis.youtube-api-key:}")String key){this.key=key;var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());factory.setReadTimeout(Duration.ofSeconds(10));client=RestClient.builder().baseUrl("https://www.googleapis.com/youtube/v3").requestFactory(factory).build();}
    public boolean configured(){return key!=null&&!key.isBlank();}
    public Eligibility inspect(String videoId){if(videoId==null||!videoId.matches("[A-Za-z0-9_-]{11}"))throw new IllegalArgumentException("Invalid video ID.");if(!configured())return unavailable("VERIFICATION_UNAVAILABLE");try{return parse(client.get().uri(uri->uri.path("/videos").queryParam("part","contentDetails,status,snippet").queryParam("id",videoId).queryParam("key",key).build()).retrieve().body(JsonNode.class),videoId);}catch(RuntimeException failure){return unavailable("VERIFICATION_UNAVAILABLE");}}
    static Eligibility parse(JsonNode body,String videoId){
        if(body==null||!body.path("items").isArray())return unavailable("VIDEO_UNAVAILABLE");
        for(var row:body.path("items")){if(!videoId.equals(row.path("id").asText()))continue;var status=row.path("status");String live=row.path("snippet").path("liveBroadcastContent").asText();if(!"none".equals(live))return new Eligibility(false,null,true,"LIVE_UNSUPPORTED");if(!"public".equals(status.path("privacyStatus").asText())||!"processed".equals(status.path("uploadStatus").asText())||!status.path("embeddable").asBoolean())return unavailable("VIDEO_UNAVAILABLE");try{Duration duration=Duration.parse(row.path("contentDetails").path("duration").asText());if(duration.isNegative()||duration.isZero())return unavailable("DURATION_UNAVAILABLE");return new Eligibility(true,Math.addExact(duration.getSeconds(),duration.getNano()>0?1:0),false,null);}catch(RuntimeException malformed){return unavailable("DURATION_UNAVAILABLE");}}
        return unavailable("VIDEO_UNAVAILABLE");
    }
    private static Eligibility unavailable(String reason){return new Eligibility(false,null,false,reason);}
}
