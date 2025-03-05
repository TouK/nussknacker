package pl.touk.nussknacker.engine.kafka.source.flink

import io.circe.Json
import pl.touk.nussknacker.engine.api.generics.MethodTypeInfo
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.{ClassDefinition, StaticMethodDefinition}
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryProcessConfigCreator.ResultsHolders

import KafkaSourceFactoryMixin.{SampleKey, SampleValue}

class KafkaSourceFactoryDefinitionExtractorSpec extends KafkaSourceFactoryProcessMixin {

  test(
    "should extract valid type definitions from source based on DynamicComponent with explicit type definitions"
  ) {
    val extractedTypes = modelData.modelDefinitionWithClasses.classDefinitions.all

    // Here we are checking explicit type extraction for sources based on DynamicComponent
    // with defined explicit type extraction.
    // It is important that SampleKey and SampleValue are used only by source of that kind,
    // and they must not be returned by other services.
    extractedTypes should contain allOf (
      ClassDefinition(
        Typed.genericTypeClass(classOf[SampleKey], Nil),
        Map(
          "partOne"  -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "partOne", None)),
          "partTwo"  -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[Long]), "partTwo", None)),
          "toString" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toString", None)),
          "originalDisplay" -> List(
            StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "originalDisplay", None)
          ),
          "asJson" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[Json]), "asJson", None))
        ),
        Map.empty
      ),
      ClassDefinition(
        Typed.genericTypeClass(classOf[SampleValue], Nil),
        Map(
          "id"       -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "id", None)),
          "field"    -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "field", None)),
          "toString" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "toString", None)),
          "originalDisplay" -> List(
            StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[String]), "originalDisplay", None)
          ),
          "asJson" -> List(StaticMethodDefinition(MethodTypeInfo(Nil, None, Typed[Json]), "asJson", None))
        ),
        Map.empty
      )
    )
  }

  override protected val resultHolders: () => ResultsHolders = () =>
    KafkaSourceFactoryDefinitionExtractorSpec.resultsHolders

}

object KafkaSourceFactoryDefinitionExtractorSpec extends Serializable {

  private val resultsHolders = new ResultsHolders

}
