package dev.maboullaite.fhemni.following;
import static dev.maboullaite.fhemni.government.GovernmentViews.*;
import dev.maboullaite.fhemni.government.GovernmentRepository;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
@Service
public class UpdateFeedService {
    private final FollowService follows;private final JdbcClient jdbc;private final GovernmentRepository repository;
    public UpdateFeedService(FollowService follows,JdbcClient jdbc,GovernmentRepository repository){this.follows=follows;this.jdbc=jdbc;this.repository=repository;}
    public List<PublishedUpdate> between(UUID user,Instant from,Instant until){var targets=follows.targets(user);if(targets.isEmpty())return List.of();return jdbc.sql("SELECT payload FROM government_publication_events WHERE published_at>=:fromDate AND published_at<:untilDate ORDER BY published_at DESC,id DESC").param("fromDate",from.atOffset(ZoneOffset.UTC)).param("untilDate",until.atOffset(ZoneOffset.UTC)).query(String.class).list().stream().map(json->repository.decode(json,PublishedUpdate.class)).filter(update->targets.stream().anyMatch(target->switch(target.type()){case "TOPIC"->update.topicCodes().contains(target.id());case "REGION"->update.regionCodes().contains(target.id());case "COMMITMENT"->update.subjectType().equals("COMMITMENT")&&update.subjectId().equals(target.id());default->false;})).toList();}
    public Page<PublishedUpdate> list(UUID user,int page){if(page<1||page>100000)throw new IllegalArgumentException("Invalid page.");var rows=between(user,Instant.EPOCH,Instant.now().plusSeconds(1));int start=Math.min((page-1)*10,rows.size());return new Page<>(rows.subList(start,Math.min(start+10,rows.size())),page,10,rows.size());}
}
