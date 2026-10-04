package dev.maboullaite.fhemni.memberanalysis;
import java.time.Instant;
import java.util.UUID;
import dev.maboullaite.fhemni.model.VideoReport;
public record MemberJob(UUID id,String videoUrl,String videoId,String language,String state,Instant createdAt,Instant updatedAt,VideoReport report,String errorCode) {}
