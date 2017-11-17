package pl.touk.nussknacker.engine.standalone.http

import argonaut.Argonaut.{jArrayElements, jObjectFields, jString}
import argonaut.{DecodeJson, Json}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.{MethodToInvoke, Service}
import pl.touk.nussknacker.engine.api.process.{SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.standalone.api.{ResponseEncoder, StandaloneGetFactory}
import pl.touk.nussknacker.engine.standalone.api.types.GenericResultType
import pl.touk.nussknacker.engine.standalone.utils.{JsonStandaloneSourceFactory, StandaloneContext, StandaloneContextLifecycle, StandaloneSinkFactory}
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import scala.concurrent.Future

class TestConfigCreator extends EmptyProcessConfigCreator {

  private implicit val decoder = DecodeJson.derive[Request]

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "request1-post-source" -> WithCategories(new JsonStandaloneSourceFactory[Request]),
    "request1-get-source" -> WithCategories(RequestGetSource)
  )

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
    "response-sink" -> WithCategories(new StandaloneSinkFactory)
  )

  override def services(config: Config): Map[String, WithCategories[Service]] = Map(
    "lifecycleService" -> WithCategories(LifecycleService)
  )


  object RequestGetSource extends StandaloneGetFactory[Request] {

    private val encoder = BestEffortJsonEncoder(failOnUnkown = true)

    override def clazz: Class[_] = classOf[Request]

    override def testDataParser: Option[TestDataParser[Request]] = None

    override def parse(parameters: Map[String, List[String]]): Request = {
      def takeFirst(id: String) = parameters.getOrElse(id, List()).headOption.getOrElse("")
      Request(takeFirst("field1"), takeFirst("field2"))
    }

    override def responseEncoder = Some(new ResponseEncoder[Request] {
      override def toJsonResponse(input: Request, result: List[Any]): GenericResultType[Json] = {
        Right(jObjectFields("inputField1" -> jString(input.field1), "list" -> jArrayElements(result.map(encoder.encode):_*)))
      }
    })
  }


}

case class Request(field1: String, field2: String)

object LifecycleService extends Service with StandaloneContextLifecycle {

  var opened: Boolean = false
  var closed: Boolean = false


  override def open(context: StandaloneContext): Unit = {
    opened = true
  }

  override def close(): Unit = {
    closed = true
  }

  @MethodToInvoke
  def invoke(): Future[Unit] = {
    Future.successful(())
  }
}

