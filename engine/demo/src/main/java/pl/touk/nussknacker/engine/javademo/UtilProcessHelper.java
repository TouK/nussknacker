package pl.touk.nussknacker.engine.javademo;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class UtilProcessHelper {

    public String mapToJson(java.util.Map<String, String> map) throws IOException {
        return new ObjectMapper().writeValueAsString(map);
    }
}
