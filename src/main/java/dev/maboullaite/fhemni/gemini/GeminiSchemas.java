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

    static JsonNode programmeExtraction(ObjectMapper mapper) {
        return read(mapper, """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "partyCode": {"type": "string", "enum": ["RNI", "PAM", "PI", "USFP", "MP", "PPS", "UC", "PJD", "MDS", "FFD", "FGD"]},
                    "electionYear": {"type": "integer", "enum": [2026]},
                    "official2026Programme": {"type": "boolean"},
                    "sourceLabel": {"type": "string"},
                    "sourceLanguage": {"type": "string"},
                    "sourceSnapshot": {"type": "string"},
                    "title": {"$ref": "#/$defs/localizedText"},
                    "summary": {"$ref": "#/$defs/localizedText"},
                    "warnings": {"type": "array", "items": {"type": "string"}},
                    "promises": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "properties": {
                          "slug": {"type": "string"},
                          "topic": {"type": "string"},
                          "title": {"$ref": "#/$defs/localizedText"},
                          "promiseText": {"type": "string"},
                          "sourceLocator": {"type": "string"},
                          "mechanism": {"type": "string"},
                          "financing": {"type": "string"}
                        },
                        "required": ["slug", "topic", "title", "promiseText", "sourceLocator", "mechanism", "financing"]
                      }
                    }
                  },
                  "$defs": {
                    "localizedText": {
                      "type": "object",
                      "additionalProperties": false,
                      "properties": {
                        "ar": {"type": "string"},
                        "fr": {"type": "string"},
                        "en": {"type": "string"}
                      },
                      "required": ["ar", "fr", "en"]
                    }
                  },
                  "required": ["partyCode", "electionYear", "official2026Programme", "sourceLabel", "sourceLanguage", "sourceSnapshot", "title", "summary", "warnings", "promises"]
                }
                """);
    }

    static JsonNode programmeFeasibility(ObjectMapper mapper) {
        return read(mapper, """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "assessments": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "properties": {
                          "promiseSlug": {"type": "string"},
                          "verdict": {"type": "string", "enum": ["POSSIBLE", "HARD", "NOT_ACHIEVABLE", "INSUFFICIENT_DATA"]},
                          "summary": {"$ref": "#/$defs/localizedText"},
                          "requirements": {"$ref": "#/$defs/localizedText"},
                          "assumptions": {"$ref": "#/$defs/localizedText"},
                          "calculationNotes": {"$ref": "#/$defs/localizedText"},
                          "evidence": {
                            "type": "array",
                            "items": {
                              "type": "object",
                              "additionalProperties": false,
                              "properties": {
                                "publisher": {"type": "string"},
                                "title": {"type": "string"},
                                "url": {"type": "string"},
                                "publishedOn": {"type": ["string", "null"]},
                                "note": {"type": "string"}
                              },
                              "required": ["publisher", "title", "url", "publishedOn", "note"]
                            }
                          }
                        },
                        "required": ["promiseSlug", "verdict", "summary", "requirements", "assumptions", "calculationNotes", "evidence"]
                      }
                    }
                  },
                  "$defs": {
                    "localizedText": {
                      "type": "object",
                      "additionalProperties": false,
                      "properties": {
                        "ar": {"type": "string"},
                        "fr": {"type": "string"},
                        "en": {"type": "string"}
                      },
                      "required": ["ar", "fr", "en"]
                    }
                  },
                  "required": ["assessments"]
                }
                """);
    }

    static JsonNode programmeChat(ObjectMapper mapper) {
        return read(mapper, """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "answer": {"type": "string"},
                    "basis": {"type": "string", "enum": ["PROGRAMME", "FEASIBILITY", "BOTH", "NOT_FOUND"]},
                    "citationIds": {
                      "type": "array",
                      "items": {"type": "string"}
                    }
                  },
                  "required": ["answer", "basis", "citationIds"]
                }
                """);
    }

    static JsonNode programmeMediaScript(ObjectMapper mapper) {
        return read(mapper, """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "properties": {
                    "headline": {"type": "string"},
                    "segments": {
                      "type": "array",
                      "minItems": 14,
                      "maxItems": 18,
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "properties": {
                          "message": {"type": "string"},
                          "narration": {"type": "string"},
                          "sourceRefs": {
                            "type": "array",
                            "minItems": 1,
                            "items": {"type": "string"}
                          }
                        },
                        "required": ["message", "narration", "sourceRefs"]
                      }
                    }
                  },
                  "required": ["headline", "segments"]
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
