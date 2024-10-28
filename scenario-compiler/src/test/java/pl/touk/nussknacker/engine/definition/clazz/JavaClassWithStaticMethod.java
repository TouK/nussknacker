package pl.touk.nussknacker.engine.definition.clazz;

import pl.touk.nussknacker.engine.api.Hidden;

public class JavaClassWithStaticMethod {

    public static String someAllowedParameterlessStaticMethod() {
        return "allowed";
    }

    @Hidden
    public static String someHiddenParameterlessStaticMethod() {
        return "hidden";
    }
}
