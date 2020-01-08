package pl.touk.nussknacker.engine.javaapi.process;

import pl.touk.nussknacker.engine.api.dict.DictDefinition;
import pl.touk.nussknacker.engine.api.process.LanguageConfiguration;
import pl.touk.nussknacker.engine.api.process.WithCategories;
import scala.collection.immutable.List$;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExpressionConfig implements Serializable {

    private final Map<String, WithCategories<Object>> globalProcessVariables;

    private final List<WithCategories<String>> globalImports;

    private final LanguageConfiguration languages;

    private final boolean optimizeCompilation;

    private final boolean strictTypeChecking;

    private final Map<String, WithCategories<DictDefinition>> dictionaries;

    private final boolean hideMetaVariable;

    public ExpressionConfig(Map<String, WithCategories<Object>> globalProcessVariables, List<WithCategories<String>> globalImports) {
        this(globalProcessVariables, globalImports, new LanguageConfiguration(List$.MODULE$.empty()), true, true, Collections.emptyMap(), false);
    }

    public ExpressionConfig(Map<String, WithCategories<Object>> globalProcessVariables, List<WithCategories<String>> globalImports,
                            LanguageConfiguration languages, boolean optimizeCompilation, boolean strictTypeChecking,
                            Map<String, WithCategories<DictDefinition>> dictionaries, boolean hideMetaVariable) {
        this.globalProcessVariables = globalProcessVariables;
        this.globalImports = globalImports;
        this.languages = languages;
        this.optimizeCompilation = optimizeCompilation;
        this.strictTypeChecking = strictTypeChecking;
        this.dictionaries = dictionaries;
        this.hideMetaVariable = hideMetaVariable;
    }

    public Map<String, WithCategories<Object>> getGlobalProcessVariables() {
        return globalProcessVariables;
    }

    public List<WithCategories<String>> getGlobalImports() {
        return globalImports;
    }

    public LanguageConfiguration getLanguages() {
        return languages;
    }

    public boolean isOptimizeCompilation() {
        return optimizeCompilation;
    }

    public boolean isStrictTypeChecking() {
        return strictTypeChecking;
    }

    public Map<String, WithCategories<DictDefinition>> getDictionaries() {
        return dictionaries;
    }

    public boolean isHideMetaVariable() {
        return hideMetaVariable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpressionConfig that = (ExpressionConfig) o;
        return optimizeCompilation == that.optimizeCompilation &&
                strictTypeChecking == that.strictTypeChecking &&
                Objects.equals(globalProcessVariables, that.globalProcessVariables) &&
                Objects.equals(globalImports, that.globalImports) &&
                Objects.equals(languages, that.languages) &&
                Objects.equals(dictionaries, that.dictionaries) &&
                Objects.equals(hideMetaVariable, that.hideMetaVariable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalProcessVariables, globalImports, languages, optimizeCompilation, strictTypeChecking, dictionaries, hideMetaVariable);
    }

}
