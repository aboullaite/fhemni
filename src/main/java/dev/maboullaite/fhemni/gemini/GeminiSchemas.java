package dev.maboullaite.fhemni.gemini;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class GeminiSchemas {

    private GeminiSchemas() {
    }

    static JsonNode videoAnalysis(ObjectMapper mapper) {
        return read(mapper, """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "title": {"type": "string"},
                    "summary": {"type": "string"},
                    "detailedSummary": {"type": "string"},
                    "participants": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "properties": {
                          "name": {"type": "string"},
                          "role": {"type": "string"}
                        },
                        "required": ["name", "role"]
                      }
                    },
                    "chapters": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "properties": {
                          "title": {"type": "string"},
                          "startSeconds": {"type": "integer", "minimum": 0},
                          "summary": {"type": "string"}
                        },
                        "required": ["title", "startSeconds", "summary"]
                      }
                    },
                    "claims": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "properties": {
                          "id": {"type": "string"},
                          "statement": {"type": "string"},
                          "speaker": {"type": "string"},
                          "startSeconds": {"type": "integer", "minimum": 0},
                          "kind": {"type": "string", "enum": ["FACT", "OPINION", "PROPOSAL", "PREDICTION"]}
                        },
                        "required": ["id", "statement", "speaker", "startSeconds", "kind"]
                      }
                    },
                    "suggestedQuestions": {
                      "type": "array",
                      "items": {"type": "string"}
                    }
                  },
                  "required": ["title", "summary", "detailedSummary", "participants", "chapters", "claims", "suggestedQuestions"]
                }
                """);
    }

    private static JsonNode read(ObjectMapper mapper, String json) {
        try {
            return mapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid embedded Gemini response schema", exception);
        }
    }
}
