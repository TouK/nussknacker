package pl.touk.nussknacker.engine.javademo;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import pl.touk.nussknacker.engine.api.HideToString;

public class UtilProcessHelper implements HideToString {

    public String mapToJson(java.util.Map<String, String> map) throws IOException {
        return new ObjectMapper().writeValueAsString(map);
    }
}
