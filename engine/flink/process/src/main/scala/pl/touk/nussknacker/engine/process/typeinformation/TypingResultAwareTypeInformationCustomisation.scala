package pl.touk.nussknacker.engine.process.typeinformation

import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult

/*
  Implementations should be registered via ServiceLoader mechanism. This trait allows to add custom TypeInformationDetection
  for specific classes
 */
trait TypingResultAwareTypeInformationCustomisation {

  def customise(originalDetection: TypingResultAwareTypeInformationDetection): PartialFunction[TypingResult, TypeInformation[_]]

}
