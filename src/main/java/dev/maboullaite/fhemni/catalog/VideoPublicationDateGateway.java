package dev.maboullaite.fhemni.catalog;

import java.time.LocalDate;

public interface VideoPublicationDateGateway {

    LocalDate fetch(String youtubeVideoId);
}
