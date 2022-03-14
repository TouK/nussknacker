package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.source

import com.typesafe.scalalogging.LazyLogging
import RequestResponsePostSourceWithContextId.{InputWithCorrelationId, SourceOutputVariableName}
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.requestresponse.api.RequestResponsePostSource

//TODO remove this file after NU 1.3 allows to pass contextId parameter

object RequestResponsePostSourceWithContextId {
  val SourceOutputVariableName = "input"
  case class InputWithCorrelationId(input: Any, correlationId: String)
}

trait RequestResponsePostSourceWithContextId[T] extends RequestResponsePostSource[T] with LazyLogging {
  //Since NU 1.2 removed custom contextId parameter
  override def transform(record: Any): Context = {
    record match {
      case InputWithCorrelationId(input, correlationId) => Context(correlationId).withVariable(SourceOutputVariableName, input)
      case input =>
        logger.info("Input was not wrapped in InputWithCorrelationId - assuming it is test from file. Setting correlationId to: 'test-from-file'.")
        Context("test-from-file").withVariable(SourceOutputVariableName, input)
    }
  }

}
