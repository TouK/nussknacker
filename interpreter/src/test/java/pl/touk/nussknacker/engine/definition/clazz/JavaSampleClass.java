package pl.touk.nussknacker.engine.definition.clazz;

public class JavaSampleClass extends SampleAbstractClass implements SampleInterface {
    public int foo;
    public String bar;

    public String getBeanProperty() {
        return "";
    }

    public boolean isBooleanProperty() {
        return false;
    }

    public String getNotProperty(int foo) {
        return "";
    }

}
