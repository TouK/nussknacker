package pl.touk.nussknacker.engine.language.json

import pl.touk.nussknacker.engine.api.typed.typing._

import scala.collection.immutable.ListMap

object UnknownTypeToJsonConverter {

  implicit class UnknownTypeToJsonConverterOps(typ: TypingResult) {

    def unknownToJson: TypingResult = typ match {
      case Unknown => Typed.json
      case obj @ TypedObjectTypingResult(fields, _, _) =>
        obj.copy(fields = ListMap(fields.toList.map { case (fieldName, fieldValue) =>
          fieldName -> fieldValue.unknownToJson
        }: _*))
      case clazz @ TypedClass(_, params) => clazz.copy(params = params.map(_.unknownToJson))
      case other                         => other
    }

  }

}
