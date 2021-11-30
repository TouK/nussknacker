package pl.touk.nussknacker.sql.utils

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.lite.api.utils.sinks.LazyParamSink
import pl.touk.nussknacker.engine.requestresponse.utils.JsonRequestResponseSourceFactory
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

//TODO: extract to separate, tests module
class RequestResponseConfigCreator extends EmptyProcessConfigCreator {

  private val Category = "Test"

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = {
    Map(
      "request" -> WithCategories(new JsonRequestResponseSourceFactory[StandaloneRequest], Category))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "response" -> WithCategories(ResponseSinkFactory, Category))
  }
}

@JsonCodec case class StandaloneRequest(id: Int)

@JsonCodec case class StandaloneResponse(name: String, count: Option[Long] = None) extends DisplayJsonWithEncoder[StandaloneResponse]

object ResponseSinkFactory extends SinkFactory {

  @MethodToInvoke
  def invoke(@ParamName("name") name: LazyParameter[String], @ParamName("count") count: LazyParameter[Option[Long]]): Sink = new ResponseSink(name, count)
}

class ResponseSink(nameParam: LazyParameter[String], countParam: LazyParameter[Option[Long]]) extends LazyParamSink[AnyRef] {
  override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] =
    nameParam.product(countParam).map {
      case (name, count) => StandaloneResponse(name, count)
    }
}
