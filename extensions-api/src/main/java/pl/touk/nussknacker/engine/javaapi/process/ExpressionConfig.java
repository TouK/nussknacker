package pl.touk.nussknacker.engine.javaapi.process;

import pl.touk.nussknacker.engine.api.dict.DictDefinition;
import pl.touk.nussknacker.engine.api.process.ExpressionConfig$;
import pl.touk.nussknacker.engine.api.process.LanguageConfiguration;
import pl.touk.nussknacker.engine.api.process.WithCategories;
//TODO: it's deprecated in 2.13
import scala.collection.JavaConverters;
import scala.collection.immutable.List$;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ExpressionConfig implements Serializable {

    private final Map<String, WithCategories<Object>> globalProcessVariables;

    private final List<String> globalImports;

    private final List<Class<?>> additionalClasses;

    private final LanguageConfiguration languages;

    private final boolean optimizeCompilation;

    private final Map<String, DictDefinition> dictionaries;

    private final boolean hideMetaVariable;

    private final boolean methodExecutionForUnknownAllowed;

    private final boolean dynamicPropertyAccessAllowed;

    public ExpressionConfig(Map<String, WithCategories<Object>> globalProcessVariables, List<String> globalImports) {
        this(globalProcessVariables, globalImports, JavaConverters.seqAsJavaList(ExpressionConfig$.MODULE$.defaultAdditionalClasses()), new LanguageConfiguration(List$.MODULE$.empty()), true, Collections.emptyMap(), false, false, false);
    }

    public ExpressionConfig(Map<String, WithCategories<Object>> globalProcessVariables, List<String> globalImports,
                            List<Class<?>> additionalClasses, LanguageConfiguration languages, boolean optimizeCompilation,
                            Map<String, DictDefinition> dictionaries, boolean hideMetaVariable, boolean methodExecutionForUnknownAllowed,
                            boolean dynamicPropertyAccessAllowed) {
        this.globalProcessVariables = globalProcessVariables;
        this.globalImports = globalImports;
        this.additionalClasses = additionalClasses;
        this.languages = languages;
        this.optimizeCompilation = optimizeCompilation;
        this.dictionaries = dictionaries;
        this.hideMetaVariable = hideMetaVariable;
        this.methodExecutionForUnknownAllowed = methodExecutionForUnknownAllowed;
        this.dynamicPropertyAccessAllowed = dynamicPropertyAccessAllowed;
    }

    public Map<String, WithCategories<Object>> getGlobalProcessVariables() {
        return globalProcessVariables;
    }

    public List<String> getGlobalImports() {
        return globalImports;
    }

    public List<Class<?>> getAdditionalClasses() {
        return additionalClasses;
    }

    public LanguageConfiguration getLanguages() {
        return languages;
    }

    public boolean isOptimizeCompilation() {
        return optimizeCompilation;
    }

    public Map<String, DictDefinition> getDictionaries() {
        return dictionaries;
    }

    public boolean isHideMetaVariable() {
        return hideMetaVariable;
    }

    public boolean isMethodExecutionForUnknownAllowed() {
        return methodExecutionForUnknownAllowed;
    }

    public boolean isDynamicPropertyAccessAllowed() {
        return dynamicPropertyAccessAllowed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExpressionConfig that = (ExpressionConfig) o;
        return optimizeCompilation == that.optimizeCompilation &&
                Objects.equals(globalProcessVariables, that.globalProcessVariables) &&
                Objects.equals(globalImports, that.globalImports) &&
                Objects.equals(additionalClasses, that.additionalClasses) &&
                Objects.equals(languages, that.languages) &&
                Objects.equals(dictionaries, that.dictionaries) &&
                Objects.equals(hideMetaVariable, that.hideMetaVariable) &&
                Objects.equals(methodExecutionForUnknownAllowed, that.methodExecutionForUnknownAllowed) &&
                Objects.equals(dynamicPropertyAccessAllowed, that.dynamicPropertyAccessAllowed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(globalProcessVariables, globalImports, additionalClasses, languages, optimizeCompilation,
                dictionaries, hideMetaVariable, methodExecutionForUnknownAllowed, dynamicPropertyAccessAllowed);
    }

}
