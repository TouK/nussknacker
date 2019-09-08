package pl.touk.nussknacker.engine.standalone.http

import java.nio.charset.StandardCharsets

import argonaut.Argonaut.{jArrayElements, jObjectFields, jString}
import argonaut.{DecodeJson, Json}
import com.typesafe.config.Config
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.{JobData, MethodToInvoke, Service}
import pl.touk.nussknacker.engine.api.process.{SinkFactory, Source, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.test.TestDataParser
import pl.touk.nussknacker.engine.standalone.api.{ResponseEncoder, StandaloneGetSource, StandalonePostSource, StandaloneSourceFactory}
import pl.touk.nussknacker.engine.standalone.api.types.GenericResultType
import pl.touk.nussknacker.engine.standalone.utils._
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import scala.concurrent.Future

class TestConfigCreator extends EmptyProcessConfigCreator {

  private implicit val decoder = DecodeJson.derive[Request]

  override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "request1-post-source" -> WithCategories(new JsonStandaloneSourceFactory[Request]),
    "request1-get-source" -> WithCategories(RequestGetSourceFactory),
    "genericGetSource" -> WithCategories(new TypedMapStandaloneSourceFactory)
  )

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
    "response-sink" -> WithCategories(new StandaloneSinkFactory)
  )

  override def services(config: Config): Map[String, WithCategories[Service]] = Map(
    "lifecycleService" -> WithCategories(LifecycleService)
  )


  object RequestGetSourceFactory extends StandaloneSourceFactory[Request] {

    private val encoder = BestEffortJsonEncoder(failOnUnkown = true)

    override def clazz: Class[_] = classOf[Request]

    @MethodToInvoke
    def create(): Source[Request] = {
      new StandaloneGetSource[Request] {
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

  }


}

@JsonCodec case class Request(field1: String, field2: String)

object LifecycleService extends Service with StandaloneContextLifecycle {

  var opened: Boolean = false
  var closed: Boolean = false


  override def open(jobData: JobData, context: StandaloneContext): Unit = {
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

