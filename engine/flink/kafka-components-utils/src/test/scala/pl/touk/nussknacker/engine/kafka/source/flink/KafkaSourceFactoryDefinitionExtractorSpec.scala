package pl.touk.nussknacker.engine.kafka.source.flink

import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, StaticMethodInfo}
import KafkaSourceFactoryMixin.{SampleKey, SampleValue}
import io.circe.Json
import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo

class KafkaSourceFactoryDefinitionExtractorSpec extends KafkaSourceFactoryProcessMixin {

  test(
    "should extract valid type definitions from source based on GenericNodeTransformation with explicit type definitions"
  ) {
    val extractedTypes = modelDefinitionWithTypes.typeDefinitions.all

    // Here we are checking explicit type extraction for sources based on GenericNodeTransformation
    // with defined explicit type extraction.
    // It is important that SampleKey and SampleValue are used only by source of that kind,
    // and they must not be returned by other services.
    extractedTypes should contain allOf (
      ClazzDefinition(
        Typed.genericTypeClass(classOf[SampleKey], Nil),
        Map(
          "partOne"  -> List(StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[String]), "partOne", None)),
          "partTwo"  -> List(StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[Long]), "partTwo", None)),
          "toString" -> List(StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[String]), "toString", None)),
          "originalDisplay" -> List(
            StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[String]), "originalDisplay", None)
          ),
          "asJson" -> List(StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[Json]), "asJson", None))
        ),
        Map.empty
      ),
      ClazzDefinition(
        Typed.genericTypeClass(classOf[SampleValue], Nil),
        Map(
          "id"       -> List(StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[String]), "id", None)),
          "field"    -> List(StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[String]), "field", None)),
          "toString" -> List(StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[String]), "toString", None)),
          "originalDisplay" -> List(
            StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[String]), "originalDisplay", None)
          ),
          "asJson" -> List(StaticMethodInfo(MethodTypeInfo(Nil, None, Typed[Json]), "asJson", None))
        ),
        Map.empty
      )
    )
  }

}
