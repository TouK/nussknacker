package pl.touk.nussknacker.engine.spel;

import pl.touk.nussknacker.engine.api.Hidden;

public class JavaClassWithStaticParameterlessMethod {

    public static String someAllowedParameterlessStaticMethod() {
        return "allowed";
    }

    @Hidden
    public static String someHiddenParameterlessStaticMethod() {
        return "hidden";
    }
}
