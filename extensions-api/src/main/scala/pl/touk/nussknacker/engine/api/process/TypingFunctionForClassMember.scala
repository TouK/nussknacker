package pl.touk.nussknacker.engine.api.process

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, TypedObjectTypingResult, TypingResult}

import java.lang.reflect.Member

trait TypingFunctionForClassMember {

  def resultType(invocationTarget: SingleTypingResult): Validated[String, TypingResult]

}

object TypingFunctionForClassMember {

  def returnGenericParameterOnPosition(genericParamPosition: Int): TypingFunctionForClassMember =
    new OneGenericParameterTypingFunction(genericParamPosition, identity)

  def returnGenericParameterOnPositionWrapped(
      genericParamPosition: Int,
      wrapGenericParam: TypingResult => TypingResult
  ): TypingFunctionForClassMember =
    new OneGenericParameterTypingFunction(genericParamPosition, wrapGenericParam)

  // In this case we assume that function will be typed base on record which generic parameters are unusable
  // so we extract generic parameter based on field's type - it is for Avro's GenericRecord case purpose
  def returnRecordFieldsGenericParameterOnPositionWrapped(
      genericParamPosition: Int,
      wrapGenericParam: TypingResult => TypingResult
  ): TypingFunctionForClassMember =
    new OneRecordFieldsGenericParameterFunction(genericParamPosition, wrapGenericParam)

  private class OneGenericParameterTypingFunction(
      genericParamPosition: Int,
      wrapGenericParam: TypingResult => TypingResult
  ) extends TypingFunctionForClassMember {

    override def resultType(invocationTargetClass: SingleTypingResult): Validated[ProcessingType, TypingResult] = {
      val params = extractParameters(invocationTargetClass)
      resultTypeBasedOnGenericParam(params, invocationTargetClass)
    }

    private def extractParameters(invocationTargetClass: SingleTypingResult): List[TypingResult] = {
      invocationTargetClass.typeHintsObjType.params
    }

    protected def resultTypeBasedOnGenericParam(
        params: List[TypingResult],
        invocationTargetClass: SingleTypingResult,
    ): Validated[String, TypingResult] = {
      params
        .map(Valid(_))
        .applyOrElse(
          genericParamPosition,
          (_: Int) =>
            Invalid(
              s"Accessing generic parameter on position $genericParamPosition but type $invocationTargetClass has only ${params.size} generic parameters"
            )
        )
        .map(wrapGenericParam)
    }

  }

  private class OneRecordFieldsGenericParameterFunction(
      genericParamPosition: Int,
      wrapGenericParam: TypingResult => TypingResult
  ) extends OneGenericParameterTypingFunction(genericParamPosition, wrapGenericParam) {

    override def resultType(invocationTargetClass: SingleTypingResult): Validated[ProcessingType, TypingResult] = {
      invocationTargetClass match {
        case TypedObjectTypingResult(fields, _, _) =>
          val params = typing.mapBasedRecordUnderlyingType[java.util.Map[_, _]](fields).params
          resultTypeBasedOnGenericParam(params, invocationTargetClass)
        case other =>
          Invalid(
            s"Accessing generic parameter but type is not a record: $other"
          )
      }

    }

  }

}

final case class TypingFunctionRule(
    memberPredicate: ClassMemberPredicate,
    typingFunction: TypingFunctionForClassMember
) {

  def matchesClassMember(clazz: Class[_], member: Member): Boolean =
    memberPredicate.matchesClassMember(clazz, member)

}
