package pl.touk.nussknacker.engine.types;

public class JavaClassWithVarargs {

    public int addAll(int... values) {
        int sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
        }
        return sum;
    }

}
