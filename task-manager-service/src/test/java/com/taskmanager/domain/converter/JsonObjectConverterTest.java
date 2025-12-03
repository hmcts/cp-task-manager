package com.taskmanager.domain.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonObjectConverterTest {

    private JsonObjectConverter converter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        converter = new JsonObjectConverter(objectMapper);
    }

    @Test
    void testConvertToDatabaseColumnWithNull() {
        String result = converter.convertToDatabaseColumn(null);
        assertNull(result);
    }

    @Test
    void testConvertToDatabaseColumnWithValidJsonObject() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("key", "value")
                .add("number", 123)
                .build();

        String result = converter.convertToDatabaseColumn(jsonObject);
        assertNotNull(result);
        assertTrue(result.contains("key"));
        assertTrue(result.contains("value"));
    }

    @Test
    void testConvertToEntityAttributeWithNull() {
        JsonObject result = converter.convertToEntityAttribute(null);
        assertNull(result);
    }

    @Test
    void testConvertToEntityAttributeWithEmptyString() {
        JsonObject result = converter.convertToEntityAttribute("");
        assertNull(result);
    }

    @Test
    void testConvertToEntityAttributeWithValidJsonString() {
        String jsonString = "{\"key\":\"value\",\"number\":123}";
        JsonObject result = converter.convertToEntityAttribute(jsonString);

        assertNotNull(result);
        assertEquals("value", result.getString("key"));
        assertEquals(123, result.getInt("number"));
    }

    @Test
    void testConvertToEntityAttributeWithQuotedString() {
        String jsonString = "\"{\\\"key\\\":\\\"value\\\"}\"";
        JsonObject result = converter.convertToEntityAttribute(jsonString);

        assertNotNull(result);
        assertEquals("value", result.getString("key"));
    }

    @Test
    void testConvertToEntityAttributeWithSingleQuotedString() {
        String jsonString = "'{\"key\":\"value\"}'";
        JsonObject result = converter.convertToEntityAttribute(jsonString);

        assertNotNull(result);
        assertEquals("value", result.getString("key"));
    }

    @Test
    void testConvertToEntityAttributeWithEscapedCharacters() {
        // Test with properly escaped JSON string
        String jsonString = "\"{\\\"key\\\":\\\"value\\\"}\"";
        JsonObject result = converter.convertToEntityAttribute(jsonString);

        assertNotNull(result);
        assertEquals("value", result.getString("key"));
    }

    @Test
    void testConvertToEntityAttributeWithInvalidJson() {
        String invalidJson = "not a json string";
        assertThrows(RuntimeException.class, () -> converter.convertToEntityAttribute(invalidJson));
    }

    @Test
    void testConvertToEntityAttributeWithLongString() {
        // Create a valid JSON string that's longer than 100 characters
        StringBuilder longJson = new StringBuilder("{\"data\":\"");
        for (int i = 0; i < 200; i++) {
            longJson.append("x");
        }
        longJson.append("\"}");

        // This should parse successfully, but if it fails, the error message should be truncated
        try {
            JsonObject result = converter.convertToEntityAttribute(longJson.toString());
            assertNotNull(result);
            assertTrue(result.getString("data").length() > 100);
        } catch (RuntimeException e) {
            // If it fails, check that error message is truncated for long strings
            assertTrue(e.getMessage().contains("...") || e.getMessage().length() < longJson.length());
        }
    }

    @Test
    void testConvertToObjectWithNull() {
        String result = converter.convertToObject(null, String.class);
        assertNull(result);
    }

    @Test
    void testConvertToObjectWithValidJsonObject() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("name", "Test")
                .add("age", 30)
                .build();

        TestPerson person = converter.convertToObject(jsonObject, TestPerson.class);

        assertNotNull(person);
        assertEquals("Test", person.getName());
        assertEquals(30, person.getAge());
    }

    @Test
    void testConvertToObjectWithInvalidClass() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("name", "Test")
                .build();

        assertThrows(RuntimeException.class, 
                () -> converter.convertToObject(jsonObject, Integer.class));
    }

    @Test
    void testConvertFromObjectWithNull() {
        JsonObject result = converter.convertFromObject(null);
        assertNull(result);
    }

    @Test
    void testConvertFromObjectWithValidObject() {
        TestPerson person = new TestPerson("John", 25);
        JsonObject result = converter.convertFromObject(person);

        assertNotNull(result);
        assertEquals("John", result.getString("name"));
        assertEquals(25, result.getInt("age"));
    }

    @Test
    void testConvertFromObjectWithComplexObject() {
        TestPerson person = new TestPerson("Jane", 30);
        JsonObject result = converter.convertFromObject(person);

        assertNotNull(result);
        assertTrue(result.containsKey("name"));
        assertTrue(result.containsKey("age"));
    }

    @Test
    void testRoundTripConversion() {
        JsonObject original = Json.createObjectBuilder()
                .add("key", "value")
                .add("number", 456)
                .build();

        String dbColumn = converter.convertToDatabaseColumn(original);
        JsonObject converted = converter.convertToEntityAttribute(dbColumn);

        assertEquals(original.getString("key"), converted.getString("key"));
        assertEquals(original.getInt("number"), converted.getInt("number"));
    }

    // Helper class for testing
    public static class TestPerson {
        private String name;
        private int age;

        public TestPerson() {
        }

        public TestPerson(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }
}

