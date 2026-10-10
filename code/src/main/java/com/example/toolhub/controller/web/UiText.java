package com.example.toolhub.controller.web;

import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;

/** Presentation-only messages; REST validation contracts remain unchanged. */
@Component("uiText")
public class UiText {
    public String field(FieldError error) {
        if (error == null) return "Please check this value.";
        String code = error.getCode() == null ? "" : error.getCode();
        if ("toolRequest".equals(error.getObjectName()) && "name".equals(error.getField())
                && "NotBlank".equals(code)) return "กรุณาระบุชื่อเครื่องมือ";
        return switch (code) {
            case "NotBlank", "NotNull", "NotEmpty" -> "This field is required.";
            case "Email" -> "Enter a valid email address.";
            case "Size" -> size(error);
            case "Pattern" -> "slug".equals(error.getField())
                    ? "Use lowercase letters, numbers, and single hyphens between words."
                    : "Use an http:// or https:// URL.";
            case "Min", "Max", "DecimalMin", "DecimalMax" -> "Choose a value within the allowed range.";
            case "duplicate" -> "slug".equals(error.getField()) ? "This slug is already in use. Choose another slug."
                    : "This version already exists. Choose another version.";
            case "typeMismatch" -> "Choose a valid value.";
            default -> "Please check this value.";
        };
    }

    private String size(FieldError error) {
        var numbers = java.util.Arrays.stream(error.getArguments() == null ? new Object[0] : error.getArguments())
                .filter(Number.class::isInstance).map(Number.class::cast).mapToInt(Number::intValue).sorted().toArray();
        if (numbers.length < 2) return error.getDefaultMessage() == null ? "Check the length of this value." : error.getDefaultMessage();
        var format = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US);
        return numbers[0] == 0 ? "Use at most " + format.format(numbers[numbers.length - 1]) + " characters."
                : "Use " + format.format(numbers[0]) + " to " + format.format(numbers[numbers.length - 1]) + " characters.";
    }
}
