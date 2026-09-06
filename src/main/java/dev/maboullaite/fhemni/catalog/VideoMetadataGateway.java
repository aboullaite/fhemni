package dev.maboullaite.fhemni.catalog;

public interface VideoMetadataGateway {

    VideoMetadata fetch(String canonicalUrl, String youtubeVideoId);

    record VideoMetadata(String title, String authorName, String thumbnailUrl) {
    }
}
