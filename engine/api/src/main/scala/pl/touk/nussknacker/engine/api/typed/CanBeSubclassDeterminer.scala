package pl.touk.nussknacker.engine.api.typed

import org.apache.commons.lang3.ClassUtils
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypedClass, TypedObjectTypingResult, TypedUnion, TypingResult, Unknown}

/**
  * This class determine if type can be subclass of other type. It basically based on fact that TypingResults are
  * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult).
  * It has very similar logic to CommonSupertypeFinder
  */
private[typed] object CanBeSubclassDeterminer {

  /**
    * This method checks if `givenType` can by subclass of `superclassCandidate`
    * It will return true if `givenType` is equals to `superclassCandidate` or `givenType` "extends" `superclassCandidate`
    */
  def canBeSubclassOf(givenType: TypingResult, superclassCandidate: TypingResult): Boolean =
    (givenType, superclassCandidate) match {
      case (_, Unknown) => true
      case (Unknown, _) => true
      case (given: SingleTypingResult, superclass: TypedUnion) => canBeSubclassOf(Set(given), superclass.possibleTypes)
      case (given: TypedUnion, superclass: SingleTypingResult) => canBeSubclassOf(given.possibleTypes, Set(superclass))
      case (given: SingleTypingResult, superclass: SingleTypingResult) => singleCanBeSubclassOf(given, superclass)
      case (given: TypedUnion, superclass: TypedUnion) => canBeSubclassOf(given.possibleTypes, superclass.possibleTypes)
    }

  private def canBeSubclassOf(givenTypes: Set[SingleTypingResult], superclassCandidates: Set[SingleTypingResult]): Boolean =
    givenTypes.exists(given => superclassCandidates.exists(singleCanBeSubclassOf(given, _)))

  private def singleCanBeSubclassOf(givenType: SingleTypingResult, superclassCandidate: SingleTypingResult): Boolean = {
    def typedObjectRestrictions = superclassCandidate match {
      case superclass: TypedObjectTypingResult =>
        val firstFields = givenType match {
          case given: TypedObjectTypingResult => given.fields
          case _ => Map.empty[String, TypingResult]
        }
        superclass.fields.forall {
          case (name, typ) => firstFields.get(name).exists(canBeSubclassOf(_, typ))
        }
      case _ =>
        true
    }
    klassCanBeSubclassOf(givenType.objType, superclassCandidate.objType) && typedObjectRestrictions
  }

  private def klassCanBeSubclassOf(givenClass: TypedClass, superclassCandidate: TypedClass): Boolean = {
    def hasSameTypeParams =
    //we are lax here - the generic type may be co- or contra-variant - and we don't want to
    //throw validation errors in this case. It's better to accept to much than too little
      superclassCandidate.params.zip(givenClass.params).forall(t => canBeSubclassOf(t._1, t._2) || canBeSubclassOf(t._2, t._1))

    givenClass == superclassCandidate || ClassUtils.isAssignable(givenClass.klass, superclassCandidate.klass) && hasSameTypeParams
  }

}
