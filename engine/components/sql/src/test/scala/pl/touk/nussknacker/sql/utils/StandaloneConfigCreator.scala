package pl.touk.nussknacker.sql.utils

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.standalone.api.StandaloneSinkWithParameters
import pl.touk.nussknacker.engine.standalone.utils.JsonStandaloneSourceFactory
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

//TODO: extract to separate, standalone tests module
class StandaloneConfigCreator extends EmptyProcessConfigCreator {

  private val Category = "Test"

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    Map(
      "request" -> WithCategories(new JsonStandaloneSourceFactory[StandaloneRequest], Category))
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "response" -> WithCategories(ResponseSinkFactory, Category))
  }
}

@JsonCodec case class StandaloneRequest(id: Int)

@JsonCodec case class StandaloneResponse(name: String, count: Option[Long] = None) extends DisplayJsonWithEncoder[StandaloneResponse]

object ResponseSinkFactory extends SinkFactory {
  override def requiresOutput: Boolean = false

  @MethodToInvoke
  def invoke(@ParamName("name") name: LazyParameter[String], @ParamName("count") count: LazyParameter[Option[Long]]): Sink = new ResponseSink(name, count)
}

class ResponseSink(nameParam: LazyParameter[String], countParam: LazyParameter[Option[Long]]) extends StandaloneSinkWithParameters {
  override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] =
    nameParam.product(countParam).map {
      case (name, count) => StandaloneResponse(name, count)
    }

  override def testDataOutput: Option[Any => String] = Some({ case response: StandaloneResponse => response.asJson.spaces2 })
}
