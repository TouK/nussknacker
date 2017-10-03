package pl.touk.nussknacker.engine.javaapi.process;

import pl.touk.nussknacker.engine.api.process.ClassMemberPredicate;

import java.util.Collections;
import java.util.List;

/**
 * Settings for class extraction which is done to handle e.g. syntax suggestions in UI
 */
public class ClassExtractionSettings {

    public static final ClassExtractionSettings DEFAULT = new ClassExtractionSettings(Collections.emptyList());

    private final List<ClassMemberPredicate> blacklistedClassMemberPredicates;

    /**
     * Creates ClassExtractionSettings
     * @param blacklistedClassMemberPredicates - list of predicates to recognize blacklisted class members
     */
    public ClassExtractionSettings(List<ClassMemberPredicate> blacklistedClassMemberPredicates) {
        this.blacklistedClassMemberPredicates = blacklistedClassMemberPredicates;
    }

    public List<ClassMemberPredicate> getBlacklistedClassMemberPredicates() {
        return blacklistedClassMemberPredicates;
    }

}
