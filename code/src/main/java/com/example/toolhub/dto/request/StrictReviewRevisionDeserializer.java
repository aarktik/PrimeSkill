package com.example.toolhub.dto.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Do not silently coerce strings, booleans or fractional revisions. */
public final class StrictReviewRevisionDeserializer extends ValueDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return context.reportInputMismatch(Long.class, "expectedReviewRevision must be an integer");
        }
        return parser.getLongValue();
    }
}
