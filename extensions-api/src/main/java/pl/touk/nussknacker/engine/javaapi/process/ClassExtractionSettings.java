package pl.touk.nussknacker.engine.javaapi.process;

import pl.touk.nussknacker.engine.api.process.*;
import scala.collection.JavaConverters;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Settings for class extraction which is done to handle e.g. syntax suggestions in UI
 */
public class ClassExtractionSettings {

    public static final ClassExtractionSettings DEFAULT = new ClassExtractionSettings(
            JavaConverters.seqAsJavaListConverter(ClassExtractionSettings$.MODULE$.DefaultExcludedClasses()).asJava(),
            JavaConverters.seqAsJavaListConverter(ClassExtractionSettings$.MODULE$.DefaultExcludedMembers()).asJava(),
            JavaConverters.seqAsJavaListConverter(ClassExtractionSettings$.MODULE$.DefaultIncludedMembers()).asJava(),
            JavaConverters.seqAsJavaListConverter(ClassExtractionSettings$.MODULE$.DefaultTypingFunctionRules())
                    .asJava()
                    .stream()
                    .map(rule -> new AbstractMap.SimpleEntry<>(rule.memberPredicate(), rule.typingFunction()))
                    .collect(Collectors.toList()),
            PropertyFromGetterExtractionStrategy.AddPropertyNextToGetter$.MODULE$);

    private final List<ClassPredicate> excludeClassPredicates;

    private final List<ClassMemberPredicate> excludeClassMemberPredicates;

    private final List<ClassMemberPredicate> includeClassMemberPredicates;

    private final List<Map.Entry<ClassMemberPredicate, TypingFunctionForClassMember>> typingFunctionRules;

    private final PropertyFromGetterExtractionStrategy propertyExtractionStrategy;

    /**
     * Creates ClassExtractionSettings
     *
     * @param excludeClassPredicates       - sequence of predicates to determine hidden classes
     * @param excludeClassMemberPredicates - sequence of predicates to determine excluded class members - will be
     *                                     used all predicates that matches given class
     * @param includeClassMemberPredicates - sequence of predicates to determine included class members - will be
     *                                     used all predicates that matches given class. If none is matching,
     *                                     all non-excluded members will be visible.
     * @param propertyExtractionStrategy   - strategy for extraction property based on getter. It can be defined
     *                                     what will happen if some class has 'getField' or 'isField' method.
     *                                     It can be: added 'field' property next to 'getField', replaced 'getField' with
     *                                     'field' or leaved as it is.
     */
    public ClassExtractionSettings(List<ClassPredicate> excludeClassPredicates,
                                   List<ClassMemberPredicate> excludeClassMemberPredicates,
                                   List<ClassMemberPredicate> includeClassMemberPredicates,
                                   List<Map.Entry<ClassMemberPredicate, TypingFunctionForClassMember>> typingFunctionRules,
                                   PropertyFromGetterExtractionStrategy propertyExtractionStrategy) {
        this.excludeClassPredicates = excludeClassPredicates;
        this.excludeClassMemberPredicates = excludeClassMemberPredicates;
        this.includeClassMemberPredicates = includeClassMemberPredicates;
        this.typingFunctionRules = typingFunctionRules;
        this.propertyExtractionStrategy = propertyExtractionStrategy;
    }

    public List<ClassPredicate> getExcludeClassPredicates() {
        return excludeClassPredicates;
    }

    public List<ClassMemberPredicate> getExcludeClassMemberPredicates() {
        return excludeClassMemberPredicates;
    }

    public List<ClassMemberPredicate> getIncludeClassMemberPredicates() {
        return includeClassMemberPredicates;
    }

    public List<Map.Entry<ClassMemberPredicate, TypingFunctionForClassMember>> getTypingFunctionRules() {
        return typingFunctionRules;
    }

    public PropertyFromGetterExtractionStrategy getPropertyExtractionStrategy() {
        return propertyExtractionStrategy;
    }
}
