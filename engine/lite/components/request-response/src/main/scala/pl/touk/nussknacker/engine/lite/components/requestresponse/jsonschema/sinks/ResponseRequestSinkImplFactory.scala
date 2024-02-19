package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks

import com.typesafe.scalalogging.LazyLogging
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink

trait ResponseRequestSinkImplFactory {
  def createSink(value: LazyParameter[AnyRef], schema: Schema): Sink
}

object DefaultResponseRequestSinkImplFactory extends ResponseRequestSinkImplFactory with LazyLogging {

  override def createSink(value: LazyParameter[AnyRef], schema: Schema): Sink = new LazyParamSink[AnyRef] {

    override def prepareResponse: LazyParameter[AnyRef] =
      value

  }

}
