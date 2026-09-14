package dev.jason.gboardpatches.extension.geminitranslation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public final class GboardGeminiTranslationClientTest {
    @Test
    public void endpointUsesTheSanitizedModelAndNoQueryKey() {
        assertEquals(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent",
                GboardGeminiTranslationClient.endpointUrl("models/gemini-3.6-flash"));
        assertFalse(GboardGeminiTranslationClient.endpointUrl("x").contains("key="));
    }

    @Test
    public void promptAutoDetectsAndUsesTheAlternateLanguage() {
        String prompt = GboardGeminiTranslationClient.buildPrompt("សួស្តី", "en", "km");
        assertTrue(prompt.contains("Detect the language"));
        assertTrue(prompt.contains("translate it into English"));
        assertTrue(prompt.contains("already written in English"));
        assertTrue(prompt.contains("into Khmer instead"));
        assertTrue(prompt.contains("<text>\nសួស្តី\n</text>"));

        String noAlternate = GboardGeminiTranslationClient.buildPrompt("hi", "ja", "");
        assertFalse(noAlternate.contains("instead"));
        assertTrue(noAlternate.contains("Japanese"));
    }

    @Test
    public void requestBodyMatchesGenerateContentShape() throws Exception {
        String body = GboardGeminiTranslationClient.buildRequestBody(
                new GboardGeminiTranslationClient.Request("k", "gemini-3.6-flash", "Hello",
                        "zh-TW", ""));
        JSONObject json = new JSONObject(body);
        JSONArray contents = json.getJSONArray("contents");
        assertEquals(1, contents.length());
        assertEquals("user", contents.getJSONObject(0).getString("role"));
        String text = contents.getJSONObject(0).getJSONArray("parts").getJSONObject(0)
                .getString("text");
        assertTrue(text.contains("Traditional Chinese"));
        assertTrue(text.contains("Hello"));
        assertEquals(1, json.getJSONObject("generationConfig").getInt("candidateCount"));
        assertFalse(body.contains("\"k\""));
    }

    @Test
    public void parsesSuccessfulResponseAndStripsWrapping() {
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"```\\nBonjour\\n```\"}]},"
                + "\"finishReason\":\"STOP\"}]}";
        GboardGeminiTranslationClient.Result result =
                GboardGeminiTranslationClient.parseResponse(200, body);
        assertTrue(result.isSuccess());
        assertEquals("Bonjour", result.getText());
    }

    @Test
    public void ignoresThoughtPartsAndReportsEmptyOutput() {
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"thinking\","
                + "\"thought\":true}]},\"finishReason\":\"MAX_TOKENS\"}]}";
        GboardGeminiTranslationClient.Result result =
                GboardGeminiTranslationClient.parseResponse(200, body);
        assertFalse(result.isSuccess());
        assertEquals("Gemini stopped: MAX_TOKENS", result.getErrorMessage());
    }

    @Test
    public void surfacesApiErrorMessages() {
        GboardGeminiTranslationClient.Result unauthorized =
                GboardGeminiTranslationClient.parseResponse(400,
                        "{\"error\":{\"message\":\"API key not valid\"}}");
        assertFalse(unauthorized.isSuccess());
        assertEquals("API key not valid", unauthorized.getErrorMessage());

        GboardGeminiTranslationClient.Result html =
                GboardGeminiTranslationClient.parseResponse(503, "<html>oops</html>");
        assertEquals("HTTP 503", html.getErrorMessage());

        GboardGeminiTranslationClient.Result blocked =
                GboardGeminiTranslationClient.parseResponse(200,
                        "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");
        assertEquals("Blocked: SAFETY", blocked.getErrorMessage());
    }

    @Test
    public void translateFailsClosedWithoutKeyOrText() {
        assertEquals("Missing Gemini API key", GboardGeminiTranslationClient.translate(
                new GboardGeminiTranslationClient.Request("", "gemini-3.6-flash", "hi", "en", ""))
                .getErrorMessage());
        assertEquals("Nothing to translate", GboardGeminiTranslationClient.translate(
                new GboardGeminiTranslationClient.Request("k", "gemini-3.6-flash", "  ", "en", ""))
                .getErrorMessage());
        assertEquals("Missing Gemini model", GboardGeminiTranslationClient.translate(
                new GboardGeminiTranslationClient.Request("k", "///", "hi", "en", ""))
                .getErrorMessage());
    }

    @Test
    public void settingsSanitizeUntrustedInput() {
        assertEquals("AIzaSyABC", GboardGeminiTranslationSettings.sanitizeApiKey("  AIza SyA\nBC "));
        assertEquals("gemini-3.6-flash",
                GboardGeminiTranslationSettings.sanitizeModel("models/gemini-3.6-flash "));
        assertEquals("", GboardGeminiTranslationSettings.sanitizeModel("<>/"));
        assertEquals("••••WXYZ", GboardGeminiTranslationSettings.maskApiKey("AIzaWXYZ"));
        assertEquals("Khmer", GboardGeminiTranslationSettings.languageDisplayName("km"));
        assertEquals("Traditional Chinese",
                GboardGeminiTranslationSettings.languageDisplayName("zh-TW"));
        assertEquals("en", GboardGeminiTranslationSettings.sanitizeLanguage("\n", "en"));
    }
}
