package pl.touk.nussknacker.engine.requestresponse.http

import com.typesafe.config.ConfigFactory
import io.circe.Json
import io.circe.Json._
import io.circe.generic.JsonCodec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.{MethodToInvoke, NodeId, ProcessVersion, Service}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter.InterpreterType
import pl.touk.nussknacker.engine.requestresponse.RequestResponseInterpreter
import pl.touk.nussknacker.engine.requestresponse.api.{RequestResponseGetSource, RequestResponseSourceFactory, ResponseEncoder}
import pl.touk.nussknacker.engine.requestresponse.utils.{JsonRequestResponseSourceFactory, TypedMapRequestResponseSourceFactory}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder

import scala.concurrent.Future

class TestConfigCreator extends EmptyProcessConfigCreator {

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
    "request1-post-source" -> WithCategories(new JsonRequestResponseSourceFactory[Request]),
    "request1-get-source" -> WithCategories(RequestGetSourceFactory),
    "genericGetSource" -> WithCategories(new TypedMapRequestResponseSourceFactory)
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "lifecycleService" -> WithCategories(LifecycleService)
  )


  object RequestGetSourceFactory extends RequestResponseSourceFactory {

    private val encoder = BestEffortJsonEncoder.defaultForTests
    
    @MethodToInvoke(returnType = classOf[Request])
    def create(implicit nodeIdPassed: NodeId): Source = {
      new RequestResponseGetSource[Request] {

        override val nodeId: NodeId = nodeIdPassed

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

trait RequestResponseInterpreterTest extends Matchers {
  import pl.touk.nussknacker.engine.util.SynchronousExecutionContext.ctx

  private val modelData = LocalModelData(ConfigFactory.empty(), new TestConfigCreator)
  private val componentUseCase = ComponentUseCase.TestRuntime
  def prepareInterpreter(process: EspProcess): InterpreterType = {
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._
    val validatedInterpreter = RequestResponseInterpreter[Future](process,
      ProcessVersion.empty,
      LiteEngineRuntimeContextPreparer.noOp, modelData, Nil, ProductionServiceInvocationCollector, componentUseCase)

    validatedInterpreter shouldBe 'valid
    validatedInterpreter.toEither.right.get
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

