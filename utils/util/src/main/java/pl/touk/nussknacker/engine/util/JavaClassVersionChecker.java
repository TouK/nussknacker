package pl.touk.nussknacker.engine.util;

// Scala 2.12 does not support target > 8 so we want to fail fast otherwise - be invoking this class at the beginning of each our Apps
// Firstly loading of this class should fail because we pass -release to javac
// Secondary we check if we are in java 11 invoking method that is only available there
public class JavaClassVersionChecker {
    public static void check() {
        try {
            "".isBlank();
        } catch (NoSuchMethodError err) {
            String javaVersion = System.getProperty("java.version");
            throw new IllegalStateException("Not supported java version: " + javaVersion + ". Please use java version >= 11", err);
        }
    }
}
