package pl.touk.nussknacker.engine.util.definition

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult}

import scala.collection.immutable.ListMap

object LazyParameterUtils {

  def typedMap(params: ListMap[String, LazyParameter[AnyRef]]): LazyParameter[TypedMap] = {
    def wrapResultType(list: List[TypingResult]): TypingResult = {
      Typed.record(
        params.keys
          .zip(list)
          .map {
            case (fieldName, TypedClass(_, _ :: valueType :: Nil, _)) =>
              fieldName -> valueType
            case other =>
              throw new IllegalArgumentException(
                s"Unexpected result of type transformation returned by sequence: $other"
              )
          }
          .toMap
      )
    }
    val paramsSeq = params.toList.map { case (key, value) =>
      LazyParameter.pure(key, Typed[String]).product(value)
    }
    LazyParameter.sequence[(String, AnyRef), TypedMap](paramsSeq, seq => TypedMap(seq.toMap), wrapResultType)
  }

}
