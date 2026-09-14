package dev.jason.gboardpatches.extension.geminitranslation;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal Gemini {@code generateContent} REST client used for translation.
 *
 * <p>The request/response shaping is separated from network I/O so that prompt construction and
 * response parsing can be verified without a device.
 */
public final class GboardGeminiTranslationClient {
    static final String ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    static final String API_KEY_HEADER = "x-goog-api-key";
    static final int CONNECT_TIMEOUT_MS = 10_000;
    static final int READ_TIMEOUT_MS = 30_000;
    static final int MAX_RESPONSE_BYTES = 1 << 20;
    static final int MAX_INPUT_CHARACTERS = 4_000;

    private GboardGeminiTranslationClient() {
    }

    public static final class Request {
        final String apiKey;
        final String model;
        final String text;
        final String targetLanguage;
        final String alternateLanguage;

        public Request(String apiKey, String model, String text, String targetLanguage,
                String alternateLanguage) {
            this.apiKey = apiKey == null ? "" : apiKey;
            this.model = model == null ? "" : model;
            this.text = text == null ? "" : text;
            this.targetLanguage = targetLanguage == null ? "" : targetLanguage;
            this.alternateLanguage = alternateLanguage == null ? "" : alternateLanguage;
        }
    }

    public static final class Result {
        private final boolean success;
        private final String text;
        private final String errorMessage;

        private Result(boolean success, String text, String errorMessage) {
            this.success = success;
            this.text = text == null ? "" : text;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }

        public static Result success(String text) {
            return new Result(true, text, null);
        }

        public static Result failure(String errorMessage) {
            return new Result(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getText() {
            return text;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /** Builds the endpoint URL for the configured model. The key is sent as a header only. */
    public static String endpointUrl(String model) {
        return ENDPOINT_BASE + GboardGeminiTranslationSettings.sanitizeModel(model)
                + ":generateContent";
    }

    /** Builds the translation prompt. Auto-detects source language; flips when already in target. */
    public static String buildPrompt(String text, String targetLanguage,
            String alternateLanguage) {
        String target = GboardGeminiTranslationSettings.languageDisplayName(targetLanguage);
        String alternate = GboardGeminiTranslationSettings.languageDisplayName(alternateLanguage);
        StringBuilder prompt = new StringBuilder(256 + text.length());
        prompt.append("You are a professional translator. Detect the language of the text ")
                .append("between the <text> tags and translate it into ").append(target).append('.');
        if (alternate != null && !alternate.isEmpty()) {
            prompt.append(" If the text is already written in ").append(target)
                    .append(", translate it into ").append(alternate).append(" instead.");
        }
        prompt.append(" Preserve the meaning, tone, line breaks, emoji, numbers and formatting.")
                .append(" Respond with ONLY the translated text. Do not add quotes,")
                .append(" explanations, notes or the original text.\n<text>\n")
                .append(text)
                .append("\n</text>");
        return prompt.toString();
    }

    /** Serialises a {@code generateContent} body for the given request. */
    public static String buildRequestBody(Request request) throws JSONException {
        JSONObject part = new JSONObject().put("text",
                buildPrompt(request.text, request.targetLanguage, request.alternateLanguage));
        JSONObject content = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray().put(part));
        JSONObject generationConfig = new JSONObject()
                .put("temperature", 0.2)
                .put("candidateCount", 1);
        return new JSONObject()
                .put("contents", new JSONArray().put(content))
                .put("generationConfig", generationConfig)
                .toString();
    }

    /** Extracts translated text from a successful {@code generateContent} response. */
    public static Result parseResponse(int statusCode, String body) {
        if (body == null) {
            body = "";
        }
        JSONObject json;
        try {
            json = body.isEmpty() ? new JSONObject() : new JSONObject(body);
        } catch (JSONException malformed) {
            return Result.failure(statusCode >= 200 && statusCode < 300
                    ? "Malformed Gemini response"
                    : "HTTP " + statusCode);
        }
        if (statusCode < 200 || statusCode >= 300) {
            JSONObject error = json.optJSONObject("error");
            String message = error == null ? null : error.optString("message", null);
            if (message == null || message.isEmpty()) {
                message = "HTTP " + statusCode;
            } else if (message.length() > 160) {
                message = message.substring(0, 160) + "…";
            }
            return Result.failure(message);
        }
        JSONObject promptFeedback = json.optJSONObject("promptFeedback");
        if (promptFeedback != null && promptFeedback.has("blockReason")) {
            return Result.failure("Blocked: " + promptFeedback.optString("blockReason"));
        }
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            return Result.failure("Gemini returned no candidates");
        }
        JSONObject candidate = candidates.optJSONObject(0);
        JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        StringBuilder builder = new StringBuilder();
        if (parts != null) {
            for (int index = 0; index < parts.length(); index++) {
                JSONObject part = parts.optJSONObject(index);
                if (part == null || part.optBoolean("thought", false)) {
                    continue;
                }
                String text = part.optString("text", "");
                if (!text.isEmpty()) {
                    builder.append(text);
                }
            }
        }
        String translated = stripWrapping(builder.toString());
        if (translated.isEmpty()) {
            String finishReason = candidate == null ? "" : candidate.optString("finishReason", "");
            return Result.failure(finishReason.isEmpty()
                    ? "Gemini returned empty text"
                    : "Gemini stopped: " + finishReason);
        }
        return Result.success(translated);
    }

    /** Removes surrounding whitespace, code fences and accidental &lt;text&gt; wrappers. */
    static String stripWrapping(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("```")) {
            int firstBreak = value.indexOf('\n');
            value = firstBreak >= 0 ? value.substring(firstBreak + 1) : "";
            if (value.endsWith("```")) {
                value = value.substring(0, value.length() - 3);
            }
            value = value.trim();
        }
        if (value.startsWith("<text>") && value.endsWith("</text>")) {
            value = value.substring("<text>".length(), value.length() - "</text>".length()).trim();
        }
        return value;
    }

    /** Performs the blocking HTTPS call. Must not run on the main thread. */
    public static Result translate(Request request) {
        if (request.apiKey.isEmpty()) {
            return Result.failure("Missing Gemini API key");
        }
        String model = GboardGeminiTranslationSettings.sanitizeModel(request.model);
        if (model.isEmpty()) {
            return Result.failure("Missing Gemini model");
        }
        String text = request.text.trim();
        if (text.isEmpty()) {
            return Result.failure("Nothing to translate");
        }
        if (text.length() > MAX_INPUT_CHARACTERS) {
            return Result.failure("Text is too long (max " + MAX_INPUT_CHARACTERS + " characters)");
        }
        HttpURLConnection connection = null;
        try {
            byte[] body = buildRequestBody(new Request(request.apiKey, model, text,
                    request.targetLanguage, request.alternateLanguage))
                    .getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) new URL(endpointUrl(model)).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty(API_KEY_HEADER, request.apiKey);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String response = readBounded(stream);
            return parseResponse(status, response);
        } catch (java.net.SocketTimeoutException timeout) {
            return Result.failure("Gemini request timed out");
        } catch (java.net.UnknownHostException offline) {
            return Result.failure("No network connection");
        } catch (Throwable failure) {
            String message = failure.getMessage();
            return Result.failure(message == null || message.isEmpty()
                    ? failure.getClass().getSimpleName()
                    : message);
        } finally {
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (Throwable ignored) {
                    // Connection cleanup cannot affect the result.
                }
            }
        }
    }

    private static String readBounded(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) >= 0) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Gemini response too large");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }
}
