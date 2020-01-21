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
            JavaConverters.seqAsJavaListConverter(ClassExtractionSettings$.MODULE$.DefaultBlacklistedClasses()).asJava(),
            JavaConverters.seqAsJavaListConverter(ClassExtractionSettings$.MODULE$.DefaultBlacklistedMembers()).asJava(),
            JavaConverters.seqAsJavaListConverter(ClassExtractionSettings$.MODULE$.DefaultWhitelistedMembers()).asJava());

    private final List<ClassPredicate> blacklistedClassPredicates;

    private final List<ClassMemberPredicate> blacklistedClassMemberPredicates;

    private final List<ClassMemberPredicate> whitelistedClassMemberPredicates;

    /**
     * Creates ClassExtractionSettings
     * @param blacklistedClassPredicates - list of predicates to recognize blacklisted class
     * @param blacklistedClassMemberPredicates - list of predicates to recognize blacklisted class members
     */
    public ClassExtractionSettings(List<ClassPredicate> blacklistedClassPredicates,
                                   List<ClassMemberPredicate> blacklistedClassMemberPredicates,
                                   List<ClassMemberPredicate> whitelistedClassMemberPredicates) {
        this.blacklistedClassPredicates = blacklistedClassPredicates;
        this.blacklistedClassMemberPredicates = blacklistedClassMemberPredicates;
        this.whitelistedClassMemberPredicates = whitelistedClassMemberPredicates;
    }

    public List<ClassPredicate> getBlacklistedClassPredicates() {
        return blacklistedClassPredicates;
    }

    public List<ClassMemberPredicate> getBlacklistedClassMemberPredicates() {
        return blacklistedClassMemberPredicates;
    }

    public List<ClassMemberPredicate> getWhitelistedClassMemberPredicates() {
        return whitelistedClassMemberPredicates;
    }
}
