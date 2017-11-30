package pl.touk.nussknacker.engine.javaapi.process;

import pl.touk.nussknacker.engine.api.process.WithCategories;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExpressionConfig implements Serializable {

    private final Map<String, WithCategories<Object>> globalProcessVariables;

    private final List<WithCategories<String>> globalImports;

    public ExpressionConfig(Map<String, WithCategories<Object>> globalProcessVariables, List<WithCategories<String>> globalImports) {
        this.globalProcessVariables = globalProcessVariables;
        this.globalImports = globalImports;
    }

    public Map<String, WithCategories<Object>> getGlobalProcessVariables() {
        return globalProcessVariables;
    }

    public List<WithCategories<String>> getGlobalImports() {
        return globalImports;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpressionConfig that = (ExpressionConfig) o;
        return Objects.equals(globalProcessVariables, that.globalProcessVariables) &&
                Objects.equals(globalImports, that.globalImports);
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalProcessVariables, globalImports);
    }

}
