package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypedClass, TypedDict, TypedObjectTypingResult, TypedTaggedValue, TypedUnion, TypingResult, Unknown}

/**
  * This class determine if type can be subclass of other type. It basically based on fact that TypingResults are
  * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult).
  * It has very similar logic to CommonSupertypeFinder
  */
private[typed] object CanBeSubclassDeterminer {

  def canBeSubclassOf(first: TypingResult, sec: TypingResult): Boolean =
    (first, sec) match {
      case (_, Unknown) => true
      case (Unknown, _) => true
      case (f: SingleTypingResult, s: TypedUnion) => canBeSubclassOf(Set(f), s.possibleTypes)
      case (f: TypedUnion, s: SingleTypingResult) => canBeSubclassOf(f.possibleTypes, Set(s))
      case (f: SingleTypingResult, s: SingleTypingResult) => singleCanBeSubclassOf(f, s)
      case (f: TypedUnion, s: TypedUnion) => canBeSubclassOf(f.possibleTypes, s.possibleTypes)
    }

  private def canBeSubclassOf(firstSet: Set[SingleTypingResult], secSet: Set[SingleTypingResult]): Boolean =
    firstSet.exists(f => secSet.exists(singleCanBeSubclassOf(f, _)))

  private def singleCanBeSubclassOf(first: SingleTypingResult, sec: SingleTypingResult): Boolean = {
    def typedObjectRestrictions = sec match {
      case s: TypedObjectTypingResult =>
        val firstFields = first match {
          case f: TypedObjectTypingResult => f.fields
          case _ => Map.empty[String, TypingResult]
        }
        s.fields.forall {
          case (name, typ) => firstFields.get(name).exists(canBeSubclassOf(_, typ))
        }
      case _ =>
        true
    }
    def dictRestriction: Boolean = (first, sec) match {
      case (f: TypedDict, s: TypedDict) =>
        f.dictId == s.dictId && f.labelByKey == s.labelByKey
      case (_: TypedDict, _) =>
        false
      case (_, _: TypedDict) =>
        false
      case _ =>
        true
    }
    def taggedValueRestriction: Boolean = (first, sec) match {
      case (firstTaggedValue: TypedTaggedValue, secTaggedValue: TypedTaggedValue) =>
        firstTaggedValue.tag == secTaggedValue.tag
      case (_: TypedTaggedValue, _) => true
      case (_, _: TypedTaggedValue) => false
      case _ => true
    }
    klassCanBeSubclassOf(first.objType, sec.objType) && typedObjectRestrictions && dictRestriction && taggedValueRestriction
  }

  private def klassCanBeSubclassOf(first: TypedClass, sec: TypedClass): Boolean = {
    def hasSameTypeParams =
    //we are lax here - the generic type may be co- or contra-variant - and we don't want to
    //throw validation errors in this case. It's better to accept to much than too little
      sec.params.zip(first.params).forall(t => canBeSubclassOf(t._1, t._2) || canBeSubclassOf(t._2, t._1))

    first == sec || ClassUtils.isAssignable(first.klass, sec.klass) && hasSameTypeParams
  }

}
