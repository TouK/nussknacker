package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.common.typeutils.TypeSerializer
import org.apache.flink.api.scala.typeutils.{CaseClassTypeInfo, ScalaCaseClassSerializer}
import pl.touk.nussknacker.engine.InterpretationResult
import pl.touk.nussknacker.engine.api.PartReference
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.process.typeinformation.internal.{FixedValueSerializers, InterpretationResultMapTypeInfo}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

object InterpretationResultTypeInformation {

  def create(detection: TypeInformationDetection, validationContext: ValidationContext, outputRes: Option[TypingResult]): TypeInformation[InterpretationResult] = {
    //TODO: here we still use Kryo :/
    val reference = TypeInformation.of(classOf[PartReference])
    val output = outputRes.map(detection.forType).getOrElse(FixedValueSerializers.nullValueTypeInfo)
    val finalContext = detection.forContext(validationContext)

    val typeInfos = List(reference, output, finalContext)
    new CaseClassTypeInfo[InterpretationResult](classOf[InterpretationResult],
      Array.empty, typeInfos, List("reference", "output", "finalContext")) {
      override def createSerializer(config: ExecutionConfig): TypeSerializer[InterpretationResult] = {
        new ScalaCaseClassSerializer[InterpretationResult](classOf[InterpretationResult], typeInfos.map(_.createSerializer(config)).toArray)
      }
    }
  }

  def create(detection: TypeInformationDetection, possibleContexts: Map[String, ValidationContext]): TypeInformation[InterpretationResult] = {
    InterpretationResultMapTypeInfo(possibleContexts.mapValuesNow(create(detection, _, None)))
  }
}
