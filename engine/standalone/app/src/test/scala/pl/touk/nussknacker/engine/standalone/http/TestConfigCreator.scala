package pl.touk.nussknacker.engine.standalone.http


import io.circe.Json
import io.circe.Json._
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{MethodToInvoke, Service}
import pl.touk.nussknacker.engine.standalone.api.{ResponseEncoder, StandaloneGetSource, StandaloneSinkFactory, StandaloneSourceFactory}
import pl.touk.nussknacker.engine.standalone.utils._
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.concurrent.Future

class TestConfigCreator extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
    "request1-post-source" -> WithCategories(new JsonStandaloneSourceFactory[Request]),
    "request1-get-source" -> WithCategories(RequestGetSourceFactory),
    "genericGetSource" -> WithCategories(new TypedMapStandaloneSourceFactory),
    "jsonSchemaSource" -> WithCategories(new JsonSchemaStandaloneSourceFactory)
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    "response-sink" -> WithCategories(new StandaloneSinkFactory)
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "lifecycleService" -> WithCategories(LifecycleService)
  )


  object RequestGetSourceFactory extends StandaloneSourceFactory[Request] {

    private val encoder = BestEffortJsonEncoder.defaultForTests
    
    @MethodToInvoke
    def create(): Source = {
      new StandaloneGetSource[Request] {
        override def parse(parameters: Map[String, List[String]]): Request = {
          def takeFirst(id: String) = parameters.getOrElse(id, List()).headOption.getOrElse("")
          Request(takeFirst("field1"), takeFirst("field2"))
        }

        override def responseEncoder = Some(new ResponseEncoder[Request] {
          override def toJsonResponse(input: Request, result: List[Any]): Json = {
            obj("inputField1" -> fromString(input.field1), "list" -> arr(result.map(encoder.encode):_*))
          }
        })
      }

    }

  }


}

@JsonCodec case class Request(field1: String, field2: String)

object LifecycleService extends Service {

  var opened: Boolean = false
  var closed: Boolean = false

  def reset(): Unit = {
    opened = false
    closed = false
  }

  override def open(context: EngineRuntimeContext): Unit = {
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

