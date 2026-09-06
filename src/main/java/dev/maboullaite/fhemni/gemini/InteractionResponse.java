package dev.maboullaite.fhemni.gemini;

import java.util.List;

import dev.maboullaite.fhemni.cost.AiUsage;
import dev.maboullaite.fhemni.model.SourceReference;

record InteractionResponse(String id, String outputText, List<SourceReference> citations, AiUsage usage) {
}
