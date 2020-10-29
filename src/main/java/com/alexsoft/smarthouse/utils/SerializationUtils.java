package com.alexsoft.smarthouse.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;

public class SerializationUtils {

    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @SneakyThrows
    public static String serializeToJson(Object obj) {
        return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
    }

    @SneakyThrows
    public static Object deSerializeFromJson(String json, Class<?> clazz) {
        return OBJECT_MAPPER.readValue(json, clazz);
    }

    @SneakyThrows
    public static void serializeToFile(String path, Object obj) {
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(Paths.get(path).toFile(), obj);
    }

    @SneakyThrows
    public static <T> T deSerializeFromFile(String path, TypeReference<T> typeReference, boolean testResourcesPath) {
        Path pathToFile = testResourcesPath ? Paths.get("src","test","resources", path) : Paths.get(path);
        return OBJECT_MAPPER.readValue(pathToFile.toFile(), typeReference);
    }

}
