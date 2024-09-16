package pl.touk.nussknacker.engine.api.process

import cats.data.NonEmptyList
import io.circe.{Decoder, Encoder}
import pl.touk.nussknacker.engine.api.definition.ParameterEditor
import pl.touk.nussknacker.engine.api.typed.supertype.ReturningSingleClassPromotionStrategy
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{Hidden, HideToString}

import java.lang.reflect.{AccessibleObject, Member, Method}
import java.text.NumberFormat
import java.time.Clock
import java.time.chrono.{ChronoLocalDate, ChronoLocalDateTime, ChronoZonedDateTime}
import java.time.temporal.{ChronoUnit, Temporal, TemporalAccessor}
import java.util
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.{Calendar, Date, Optional, UUID}

// TODO: Rename to ClassDefinitionDiscoverySettings
/**
  * Settings for class extraction which is done to handle e.g. syntax suggestions in UI
  *
  * One-liner that will help to find list of interesting methods in class:
  * {{{
  * classOf[CLASS_NAME].getMethods.filterNot(m => java.lang.reflect.Modifier.isStatic(m.getModifiers)).map(_.getName)
  *   .filterNot(Set("wait", "notify", "notifyAll", "equals", "hashCode", "getClass").contains).distinct.sorted.mkString("|")
  * }}}
  *
  * @param excludeClassPredicates - sequence of predicates to determine hidden classes
  * @param excludeClassMemberPredicates - sequence of predicates to determine excluded class members - will be
  *                                       used all predicates that matches given class
  * @param includeClassMemberPredicates - sequence of predicates to determine included class members - will be
  *                                       used all predicates that matches given class. If none is matching,
  *                                       all non-excluded members will be visible.
  * @param propertyExtractionStrategy - strategy for extraction property based on getter. It can be defined
  *                                     what will happen if some class has 'getField' or 'isField' method.
  *                                     It can be: added 'field' property next to 'getField', replaced 'getField' with
  *                                     'field' or leaved as it is.
  */
case class ClassExtractionSettings(
    excludeClassPredicates: Seq[ClassPredicate],
    excludeClassMemberPredicates: Seq[ClassMemberPredicate],
    includeClassMemberPredicates: Seq[ClassMemberPredicate],
    typingFunctionRules: Seq[TypingFunctionRule],
    propertyExtractionStrategy: PropertyFromGetterExtractionStrategy
) {

  def isHidden(clazz: Class[_]): Boolean =
    excludeClassPredicates.exists(_.matches(clazz))

  def visibleMembersPredicate(clazz: Class[_]): VisibleMembersPredicate = {
    VisibleMembersPredicate(
      excludeClassMemberPredicates.filter(p => p.matchesClass(clazz)),
      NonEmptyList.fromList(includeClassMemberPredicates.filter(p => p.matchesClass(clazz)).toList)
    )
  }

  def typingFunction(clazz: Class[_], member: Member): Option[TypingFunctionForClassMember] =
    typingFunctionRules.collectFirst {
      case rule if rule.matchesClassMember(clazz, member) => rule.typingFunction
    }

}

case class VisibleMembersPredicate(
    excludePredicates: Seq[ClassMemberPredicate],
    includePredicates: Option[NonEmptyList[ClassMemberPredicate]]
) {

  def shouldBeVisible(member: Member): Boolean =
    !excludePredicates.exists(_.matchesMember(member)) && includePredicates.forall(_.exists(_.matchesMember(member)))

}

sealed trait PropertyFromGetterExtractionStrategy

object PropertyFromGetterExtractionStrategy {

  case object AddPropertyNextToGetter extends PropertyFromGetterExtractionStrategy

  case object ReplaceGetterWithProperty extends PropertyFromGetterExtractionStrategy

  case object DoNothing extends PropertyFromGetterExtractionStrategy

}

object ClassExtractionSettings {

  val ToStringMethod: String = "toString"

  val Default: ClassExtractionSettings = ClassExtractionSettings(
    DefaultExcludedClasses,
    DefaultExcludedMembers,
    DefaultIncludedMembers,
    DefaultTypingFunctionRules,
    // TODO: We should change this to ReplaceGetterWithProperty which is more natural and which not introduces ambiguity
    PropertyFromGetterExtractionStrategy.AddPropertyNextToGetter
  )

  lazy val DefaultExcludedClasses: List[ClassPredicate] = ExcludedStdClasses ++ ExcludedExtraClasses

  lazy val ExcludedStdClasses: List[ClassPredicate] =
    ExcludedVoidClasses ++ ExcludedCollectionFunctionalClasses ++ ExcludedTimeClasses ++
      List(
        // In case if someone use it for kind of meta programming
        ExactClassPredicate[Class[_]],

        // we want only boxed types
        ClassPredicate { case cl => cl.isPrimitive },
        ExactClassPredicate[ReturningSingleClassPromotionStrategy],
        // We use this type only programmable
        ClassNamePredicate("pl.touk.nussknacker.engine.spel.SpelExpressionRepr"),
      )

  lazy val ExcludedCollectionFunctionalClasses: List[ClassPredicate] = List(
    // In case if someone return function for lazy evaluation purpose
    BasePackagePredicate("java.util.function"),
    ClassNamePrefixPredicate("scala.Function"),
    BasePackagePredicate("java.util.stream"),
    // Scala collections are cumbersome to use with Spel
    BasePackagePredicate("scala.collection"),
    ClassNamePrefixPredicate("scala.Tuple"),
    ExactClassPredicate[Option[_]],
  )

  lazy val ExcludedVoidClasses: List[ClassPredicate] = List(
    // Void types
    ClassNamePredicate("void", "java.lang.Void", "scala.runtime.BoxedUnit"),
    ClassNamePrefixPredicate("scala.Unit.*"),
  )

  lazy val ExcludedTimeClasses: List[ClassPredicate] = List(
    // we want to have Chrono*Time classes, as many parameters/return types of Local/ZonedDate(Time) use them
    ExceptOfClassesPredicate(
      BasePackagePredicate("java.time.chrono"),
      ExactClassPredicate(classOf[ChronoLocalDateTime[_]], classOf[ChronoLocalDate], classOf[ChronoZonedDateTime[_]])
    ),
    ExceptOfClassesPredicate(
      BasePackagePredicate("java.time.temporal"),
      ExactClassPredicate(classOf[ChronoUnit], classOf[Temporal], classOf[TemporalAccessor])
    ),
    ExactClassPredicate(classOf[Clock]),
    BasePackagePredicate("java.time.zone"),
  )

  lazy val ExcludedExtraClasses: List[ClassPredicate] =
    List(
      // In case if there is some public method with flink's TypeInformation on serialization purpose
      BasePackagePredicate("org.apache.flink.api.common.typeinfo"),
      // java xml api is not easy to use without additional helpers, so we will skip these classes
      BasePackagePredicate("javax.xml"),
      // Not sure why these below exclusions are TODO describe why they should be here or remove it
      BasePackagePredicate("dispatch"),
      BasePackagePredicate("cats"),
      ExactClassPredicate(classOf[Decoder[_]], classOf[Encoder[_]]),
      // If cronutils are included to use Cron editor, we don't want quite a lot of unnecessary classes
      BasePackagePredicate("com.cronutils.model.field")
    )

  lazy val DefaultExcludedMembers: List[ClassMemberPredicate] =
    CommonExcludedMembers ++
      KafkaExcludedMembers ++
      AvroExcludedMembers ++
      JavaTimeExcludeMembers ++
      ExtensionExcludedMembers :+
      ReturnMemberPredicate(
        SuperClassPredicate(
          ExactClassPredicate(
            classOf[Decoder[_]],
            classOf[Encoder[_]],
            classOf[ParameterEditor]
          )
        )
      )

  lazy val CommonExcludedMembers: List[ClassMemberPredicate] =
    List(
      // We want to hide all technical methods in every class, toString can be useful so we will leave it
      AllMembersPredicate(classOf[DumpCaseClass], Set(ToStringMethod)),
      ClassMemberPredicate(
        ClassPredicate { case _ => true },
        { case m =>
          m.getName.contains("$")
        }
      ),
      ClassMemberPredicate(
        ClassPredicate { case _ => true },
        { case m: Member with AccessibleObject =>
          m.getAnnotation(classOf[Hidden]) != null
        }
      ),
      ClassMemberPredicate(
        ClassPredicate { case cl => classOf[HideToString].isAssignableFrom(cl) },
        { case m: Method =>
          m.getName == ToStringMethod && m.getParameterCount == 0
        }
      ),
      ClassMemberPredicate(
        ClassPredicate { case cl => cl.isEnum },
        { case m: Method =>
          List("declaringClass", "getDeclaringClass").contains(m.getName)
        }
      )
    )

  lazy val KafkaExcludedMembers: List[ClassMemberPredicate] = List(
    MemberNamePredicate(
      SuperClassPredicate(ClassNamePredicate("pl.touk.nussknacker.engine.kafka.source.InputMeta")),
      Set("withType", "apply", "keyParameterName")
    )
  )

  lazy val AvroExcludedMembers: List[ClassMemberPredicate] =
    List(
      MemberNamePredicate(
        SuperClassPredicate(ClassNamePredicate("org.apache.avro.generic.IndexedRecord")),
        Set("getSchema", "compareTo", "put")
      )
    )

  lazy val JavaTimeExcludeMembers: List[ClassMemberPredicate] =
    List(
      MemberNamePredicate(
        SuperClassPredicate(ClassNamePredicate("java.time.temporal.TemporalAccessor")),
        Set("adjustInto", "from")
      )
    )

  lazy val ExtensionExcludedMembers: List[ClassMemberPredicate] = List(
    MemberNamePredicate(
      SuperClassPredicate(
        ExactClassPredicate(
          classOf[java.lang.Boolean],
          classOf[Number],
          classOf[CharSequence],
        )
      ),
      Set("canCastTo", "castTo")
    )
  )

  lazy val DefaultIncludedMembers: List[ClassMemberPredicate] =
    IncludedUtilsMembers ++ IncludedSerializableMembers ++ IncludedStdMembers ++ IncludedExtensionMembers

  lazy val IncludedStdMembers: List[ClassMemberPredicate] =
    List(
      // For other std types we don't want to see anything but toString method
      MemberNamePredicate(ClassNamePrefixPredicate("java."), Set(ToStringMethod)),
      MemberNamePredicate(ClassNamePrefixPredicate("scala."), Set(ToStringMethod))
    )

  lazy val IncludedUtilsMembers: List[ClassMemberPredicate] =
    List(
      // For numeric types, strings an collections, date types we want to see all useful methods - we need this explicitly define here because
      // we have another, more general rule: IncludedStdMembers and both predicates are composed
      ClassMemberPredicate(
        SuperClassPredicate(
          ExactClassPredicate(
            classOf[java.lang.Boolean],
            classOf[Number],
            classOf[Date],
            classOf[Calendar],
            classOf[TimeUnit]
          )
        ),
        { case _ => true }
      ),
      ClassMemberPredicate(ClassNamePrefixPredicate("java.time."), { case _ => true }),
      ClassMemberPredicate(ClassNamePrefixPredicate("scala.concurrent.duration."), { case _ => true }),
      MemberNamePatternPredicate(
        SuperClassPredicate(ExactClassPredicate[CharSequence]),
        Pattern.compile(
          s"charAt|compareTo.*|concat|contains|endsWith|equalsIgnoreCase|format|indexOf|isBlank|isEmpty|join|lastIndexOf|length|matches|" +
            s"replace|replaceAll|replaceFirst|split|startsWith|strip.*|substring|toLowerCase|toUpperCase|trim|$ToStringMethod"
        )
      ),
      MemberNamePatternPredicate(
        SuperClassPredicate(ExactClassPredicate[NumberFormat]),
        Pattern.compile(s"get.*Instance|format|parse")
      ),
      MemberNamePredicate(
        SuperClassPredicate(ExactClassPredicate[util.Collection[_]]),
        Set("contains", "containsAll", "get", "indexOf", "isEmpty", "lastIndexOf", "size")
      ),
      MemberNamePredicate(
        SuperClassPredicate(ExactClassPredicate[util.Map[_, _]]),
        Set("containsKey", "containsValue", "get", "getOrDefault", "isEmpty", "size", "values", "keySet")
      ),
      MemberNamePredicate(
        SuperClassPredicate(ExactClassPredicate[Optional[_]]),
        Set("get", "isEmpty", "isPresent", "orElse")
      ),
      MemberNamePredicate(
        SuperClassPredicate(ExactClassPredicate[UUID]),
        Set(
          "clockSequence",
          "randomUUID",
          "fromString",
          "getLeastSignificantBits",
          "getMostSignificantBits",
          "node",
          "timestamp",
          ToStringMethod,
          "variant",
          "version"
        )
      ),
      MemberNamePredicate(
        SuperClassPredicate(ExactClassPredicate(classOf[Iterable[_]], classOf[Option[_]])),
        Set(
          "apply",
          "applyOrElse",
          "contains",
          "get",
          "getOrDefault",
          "head",
          "indexOf",
          "isDefined",
          "isEmpty",
          "nonEmpty",
          "orNull",
          "size",
          "tail",
          "values",
          "keys",
          "diff"
        )
      )
    )

  lazy val IncludedSerializableMembers: List[ClassMemberPredicate] =
    List(
      MemberNamePredicate(ClassNamePrefixPredicate("scala.xml."), Set(ToStringMethod)),
      MemberNamePredicate(
        ClassNamePrefixPredicate("io.circe."),
        Set("noSpaces", "noSpacesSortKeys", "spaces2", "spaces2SortKeys", "spaces4", "spaces4SortKeys", ToStringMethod)
      ),
    )

  lazy val IncludedExtensionMembers: List[ClassMemberPredicate] = List(
    MemberNamePredicate(
      SuperClassPredicate(
        ExactClassPredicate(
          classOf[Iterable[_]],
          classOf[Option[_]],
          classOf[Map[_, _]],
          classOf[java.util.Collection[_]],
          classOf[java.util.Map[_, _]]
        )
      ),
      Set("canCastTo", "castTo")
    ),
    MemberNamePredicate(ExactClassPredicate(classOf[java.lang.Object]), Set("canCastTo", "castTo")),
  )

  lazy val DefaultTypingFunctionRules: List[TypingFunctionRule] =
    List(
      TypingFunctionRule(
        MemberNamePredicate(
          SuperClassPredicate(ExactClassPredicate[util.Map[_, _]]),
          Set("get", "getOrDefault")
        ),
        TypingFunctionForClassMember.returnGenericParameterOnPosition(genericParamPosition = 1)
      ),
      TypingFunctionRule(
        MemberNamePredicate(
          SuperClassPredicate(ExactClassPredicate[util.Map[_, _]]),
          Set("values")
        ),
        TypingFunctionForClassMember.returnGenericParameterOnPositionWrapped(
          genericParamPosition = 1,
          wrapGenericParam = valueType => Typed.genericTypeClass[util.Collection[_]](List(valueType))
        )
      ),
      TypingFunctionRule(
        MemberNamePredicate(
          SuperClassPredicate(ExactClassPredicate[util.Map[_, _]]),
          Set("keySet")
        ),
        TypingFunctionForClassMember.returnGenericParameterOnPositionWrapped(
          genericParamPosition = 0,
          wrapGenericParam = valueType => Typed.genericTypeClass[util.Set[_]](List(valueType))
        )
      ),
      TypingFunctionRule(
        MemberNamePredicate(
          SuperClassPredicate(ExactClassPredicate[util.Collection[_]]),
          Set("get")
        ),
        TypingFunctionForClassMember.returnGenericParameterOnPosition(genericParamPosition = 0)
      ),
      TypingFunctionRule(
        MemberNamePredicate(
          SuperClassPredicate(ExactClassPredicate[util.Optional[_]]),
          Set("get", "orElse")
        ),
        TypingFunctionForClassMember.returnGenericParameterOnPosition(genericParamPosition = 0)
      ),
      TypingFunctionRule(
        MemberNamePredicate(
          SuperClassPredicate(ClassNamePredicate("org.apache.avro.generic.GenericRecord")),
          Set("get")
        ),
        TypingFunctionForClassMember.returnRecordFieldsGenericParameterOnPositionWrapped(
          genericParamPosition = 1,
          wrapGenericParam = identity
        )
      )
    )

  private case class DumpCaseClass()

}
