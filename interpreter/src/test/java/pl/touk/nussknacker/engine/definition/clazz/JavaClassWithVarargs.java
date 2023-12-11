package pl.touk.nussknacker.engine.definition.clazz;

public class JavaClassWithVarargs {

    public int addAllWithObjects(Object... values) {
        throw new IllegalArgumentException("");
    }

    public int addAll(int... values) {
        int sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
        }
        return sum;
    }

}
