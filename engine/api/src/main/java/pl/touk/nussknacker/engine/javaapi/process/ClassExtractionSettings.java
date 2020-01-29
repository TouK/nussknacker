package pl.touk.nussknacker.engine.javaapi.process;

import pl.touk.nussknacker.engine.api.process.ClassExtractionSettings$;
import pl.touk.nussknacker.engine.api.process.ClassMemberPredicate;
import pl.touk.nussknacker.engine.api.process.ClassPredicate;
import scala.collection.JavaConverters;

import java.util.List;

/**
 * Settings for class extraction which is done to handle e.g. syntax suggestions in UI
 */
public class ClassExtractionSettings {

    public static final ClassExtractionSettings DEFAULT = new ClassExtractionSettings(
            JavaConverters.seqAsJavaListConverter(ClassExtractionSettings$.MODULE$.DefaultExcludedClasses()).asJava(),
            JavaConverters.seqAsJavaListConverter(ClassExtractionSettings$.MODULE$.DefaultExcludedMembers()).asJava(),
            JavaConverters.seqAsJavaListConverter(ClassExtractionSettings$.MODULE$.DefaultIncludedMembers()).asJava());

    private final List<ClassPredicate> excludeClassPredicates;

    private final List<ClassMemberPredicate> excludeClassMemberPredicates;

    private final List<ClassMemberPredicate> includeClassMemberPredicates;

    /**
     * Creates ClassExtractionSettings
     * @param excludeClassPredicates - sequence of predicates to determine hidden classes
     * @param excludeClassMemberPredicates - sequence of predicates to determine excluded class members - will be
     *                                       used all predicates that matches given class
     * @param includeClassMemberPredicates - sequence of predicates to determine included class members - will be
     *                                       used all predicates that matches given class. If none is matching,
     *                                       all non-excluded members will be visible.
     */
    public ClassExtractionSettings(List<ClassPredicate> excludeClassPredicates,
                                   List<ClassMemberPredicate> excludeClassMemberPredicates,
                                   List<ClassMemberPredicate> includeClassMemberPredicates) {
        this.excludeClassPredicates = excludeClassPredicates;
        this.excludeClassMemberPredicates = excludeClassMemberPredicates;
        this.includeClassMemberPredicates = includeClassMemberPredicates;
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
}
