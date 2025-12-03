package com.taskmanager.domain.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.StringReader;

/**
 * Unified converter for bidirectional conversion between JsonObject and Object types.
 * 
 * <p>This converter implements the JPA {@link AttributeConverter} pattern for automatic
 * conversion between {@link JsonObject} entity attributes and database columns (String).
 * It also provides utility methods for converting between JsonObject and arbitrary Java objects.
 * 
 * <p>The converter handles:
 * <ul>
 *   <li>JPA entity attribute conversion (JsonObject ↔ String for database storage)</li>
 *   <li>PostgreSQL JSONB format handling (quoted strings, escaped characters)</li>
 *   <li>Java object serialization/deserialization using Jackson</li>
 * </ul>
 * 
 * <p>When used as a JPA converter (via {@link Converter} annotation), it automatically
 * converts JsonObject fields in entities to/from database columns. The database column
 * is stored as TEXT/JSONB in PostgreSQL.
 * 
 * <p><b>Usage examples:</b>
 * <pre>{@code
 * // In entity
 * @Convert(converter = JsonObjectConverter.class)
 * private JsonObject jobData;
 * 
 * // Convert JsonObject to Java object
 * MyObject obj = converter.convertToObject(jsonObject, MyObject.class);
 * 
 * // Convert Java object to JsonObject
 * JsonObject json = converter.convertFromObject(myObject);
 * }</pre>
 * 
 * @author Task Manager Service
 * @since 1.0.0
 * @see AttributeConverter
 * @see JsonObject
 */
@Converter
@Component
public class JsonObjectConverter implements AttributeConverter<JsonObject, String> {
    
    /**
     * Jackson ObjectMapper for serialization/deserialization of Java objects.
     */
    private final ObjectMapper objectMapper;
    
    /**
     * Constructs a new JsonObjectConverter with the specified ObjectMapper.
     * 
     * @param objectMapper the Jackson ObjectMapper for object conversion, must not be null
     */
    @Autowired
    public JsonObjectConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * Converts a JsonObject entity attribute to a database column value (String).
     * 
     * <p>This method is called by JPA when persisting an entity with a JsonObject field.
     * The JsonObject is converted to its JSON string representation.
     * 
     * @param attribute the JsonObject attribute from the entity, may be null
     * @return the JSON string representation, or null if attribute is null
     */
    @Override
    public String convertToDatabaseColumn(JsonObject attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.toString();
    }
    
    /**
     * Converts a database column value (String) to a JsonObject entity attribute.
     * 
     * <p>This method is called by JPA when retrieving an entity with a JsonObject field.
     * It handles PostgreSQL JSONB format which may return JSON as a quoted string.
     * 
     * <p>The method:
     * <ul>
     *   <li>Handles null and empty strings</li>
     *   <li>Strips quotes if the string is quoted (PostgreSQL JSONB format)</li>
     *   <li>Unescapes escaped characters</li>
     *   <li>Parses the JSON string to a JsonObject</li>
     * </ul>
     * 
     * @param dbData the database column value (JSON string), may be null or empty
     * @return the JsonObject representation, or null if dbData is null or empty
     * @throws RuntimeException if the string cannot be parsed as valid JSON
     */
    @Override
    public JsonObject convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            String jsonString = dbData.trim();
            
            // Handle case where JSONB is returned as a quoted string by PostgreSQL
            // e.g., "\"{...}\"" or '"{...}"'
            if ((jsonString.startsWith("\"") && jsonString.endsWith("\"")) ||
                (jsonString.startsWith("'") && jsonString.endsWith("'"))) {
                jsonString = jsonString.substring(1, jsonString.length() - 1);
                // Unescape escaped quotes and backslashes
                jsonString = jsonString.replace("\\\"", "\"")
                                      .replace("\\\\", "\\");
            }
            
            // Now parse the JSON string
            return Json.createReader(new java.io.StringReader(jsonString)).readObject();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert database string to JsonObject. Value: " + 
                (dbData != null && dbData.length() > 100 ? dbData.substring(0, 100) + "..." : dbData), e);
        }
    }
    
    /**
     * Converts a JsonObject to a Java object of the specified type.
     * 
     * <p>This utility method uses Jackson's ObjectMapper to deserialize a JsonObject
     * to a Java object. The JsonObject is first converted to a JSON string, then
     * deserialized to the target type.
     * 
     * @param <T> the target object type
     * @param jsonObject the JsonObject to convert, may be null
     * @param targetClass the target class type
     * @return the converted object, or null if jsonObject is null
     * @throws RuntimeException if conversion fails (invalid JSON or incompatible types)
     */
    public <T> T convertToObject(JsonObject jsonObject, Class<T> targetClass) {
        if (jsonObject == null) {
            return null;
        }
        
        try {
            // Convert JsonObject to JSON string
            String jsonString = jsonObject.toString();
            
            // Use Jackson ObjectMapper to deserialize to target class
            return objectMapper.readValue(jsonString, targetClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert JsonObject to " + targetClass.getSimpleName() + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Converts a Java object to a JsonObject.
     * 
     * <p>This utility method uses Jackson's ObjectMapper to serialize a Java object
     * to a JSON string, then parses it into a JsonObject using the Jakarta JSON API.
     * 
     * @param object the object to convert, may be null
     * @return the JsonObject representation, or null if object is null
     * @throws RuntimeException if conversion fails (serialization or parsing error)
     */
    public JsonObject convertFromObject(Object object) {
        if (object == null) {
            return null;
        }
        
        try {
            // Use Jackson ObjectMapper to serialize object to JSON string
            String jsonString = objectMapper.writeValueAsString(object);
            
            // Parse JSON string to JsonObject using Glassfish implementation
            try (JsonReader reader = Json.createReader(new StringReader(jsonString))) {
                return reader.readObject();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert Object to JsonObject: " + e.getMessage(), e);
        }
    }
}
