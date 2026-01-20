package org.jerish.dbdeploy.script;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for replacing placeholders in SQL content with actual parameter values.
 * Placeholders are in the format ${param_name}.
 */
@Slf4j
@Service
public class ScriptParameterHandler {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}");

    /**
     * Replace placeholders in SQL content with actual parameter values.
     * Placeholders are in the format ${param_name}.
     *
     * @param content The SQL content with placeholders
     * @param parameters The map of parameter names to values
     * @return The SQL content with placeholders replaced
     * @throws RuntimeException if a placeholder is found but no value is provided
     */
    public String replacePlaceholders(String content, Map<String, String> parameters) {
        if (content == null) {
            return null;
        }

        if (parameters == null || parameters.isEmpty()) {
            // Check if there are any placeholders in the content
            Matcher checkMatcher = PLACEHOLDER_PATTERN.matcher(content);
            if (checkMatcher.find()) {
                throw new RuntimeException("SQL contains placeholders but no parameters were provided. Found placeholder: " + checkMatcher.group());
            }
            return content;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String placeholder = matcher.group(1); // Get the parameter name without ${}
            String value = parameters.get(placeholder);

            if (value != null) {
                // Replace the placeholder with the actual value
                matcher.appendReplacement(result, Matcher.quoteReplacement(value));
                log.debug("Replaced placeholder '{}' with value '{}'", placeholder, value);
            } else {
                // Throw exception if no value is found for a placeholder
                throw new RuntimeException("No value provided for placeholder: " + placeholder);
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Check if the content contains any placeholders
     *
     * @param content The content to check
     * @return true if placeholders are found, false otherwise
     */
    public boolean containsPlaceholders(String content) {
        if (content == null) {
            return false;
        }
        return PLACEHOLDER_PATTERN.matcher(content).find();
    }

    /**
     * Extract all placeholder names from the content
     *
     * @param content The content to extract placeholders from
     * @return List of placeholder names (without the ${} wrapper)
     */
    public java.util.List<String> extractPlaceholderNames(String content) {
        java.util.List<String> placeholders = new java.util.ArrayList<>();
        
        if (content == null) {
            return placeholders;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!placeholders.contains(placeholder)) {
                placeholders.add(placeholder);
            }
        }

        return placeholders;
    }

    /**
     * Validate that all placeholders in the content have corresponding values in the parameters map
     *
     * @param content The content to validate
     * @param parameters The parameters map
     * @throws RuntimeException if any placeholder is missing a value
     */
    public void validatePlaceholders(String content, Map<String, String> parameters) {
        if (content == null) {
            return;
        }

        java.util.List<String> placeholders = extractPlaceholderNames(content);
        
        for (String placeholder : placeholders) {
            if (parameters == null || !parameters.containsKey(placeholder)) {
                throw new RuntimeException("No value provided for placeholder: " + placeholder);
            }
        }
    }
}
