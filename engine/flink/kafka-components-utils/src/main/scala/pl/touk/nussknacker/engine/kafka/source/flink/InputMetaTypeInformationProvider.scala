package pl.touk.nussknacker.engine.kafka.source.flink

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.MapTypeInfo
import org.apache.kafka.common.record.TimestampType
import pl.touk.nussknacker.engine.api.typed.typing.{TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.flink.api.typeinformation.{TypeInformationDetection, TypingResultAwareTypeInformationCustomisation}
import pl.touk.nussknacker.engine.flink.typeinformation.ConcreteCaseClassTypeInfo
import pl.touk.nussknacker.engine.kafka.source.InputMeta

object InputMetaTypeInformationProvider {

  // It should be synchronized with InputMeta.withType
  def typeInformation[K](keyTypeInformation: TypeInformation[K]): CaseClassTypeInfo[InputMeta[K]] = {
    ConcreteCaseClassTypeInfo[InputMeta[K]](
      (InputMeta.keyParameterName, keyTypeInformation),
      ("topic", TypeInformation.of(classOf[String])),
      ("partition", TypeInformation.of(classOf[Integer])),
      ("offset", TypeInformation.of(classOf[java.lang.Long])),
      ("timestamp", TypeInformation.of(classOf[java.lang.Long])),
      ("timestampType", TypeInformation.of(classOf[TimestampType])),
      ("headers", new MapTypeInfo(classOf[String], classOf[String])),
      ("leaderEpoch", TypeInformation.of(classOf[Integer]))
    )
  }
}

/**
  * Implementation of customisation for TypeInformationDetection that provides type information for InputMeta.
  */
class InputMetaTypeInformationCustomisation extends TypingResultAwareTypeInformationCustomisation {
  override def customise(originalDetection: TypeInformationDetection): PartialFunction[TypingResult, TypeInformation[_]] = {
    case a:TypedObjectTypingResult if a.objType.klass == classOf[InputMeta[_]] =>
      InputMetaTypeInformationProvider.typeInformation(originalDetection.forType(a.fields(InputMeta.keyParameterName)))
  }

}