package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.sinks

import com.typesafe.scalalogging.LazyLogging
import org.everit.json.schema.Schema
import pl.touk.nussknacker.engine.api.process.Sink
import pl.touk.nussknacker.engine.api.{LazyParameter, LazyParameterInterpreter}
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import org.json.JSONObject

trait ResponseRequestSinkImplFactory {
  def createSink(value: LazyParameter[AnyRef],
                 schema: Schema): Sink
}

object DefaultResponseRequestSinkImplFactory extends ResponseRequestSinkImplFactory with LazyLogging {

  override def createSink(value: LazyParameter[AnyRef], schema: Schema): Sink = new LazyParamSink[AnyRef] {

    override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] = {
      value.map {
        case m: java.util.Map[_, _] =>
          val obj = new JSONObject(m)
          schema.validate(obj)
          obj.toMap
        case result =>
          logger.debug("Result is not Map - skipping schema validation")
          result
      }
    }
  }
}
