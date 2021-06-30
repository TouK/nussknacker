package pl.touk.nussknacker.openapi.functional

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, EitherValues, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.definition.ServiceWithExplicitMethod
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{ServiceInvocationCollector, ToCollect}
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.openapi.ApiKeyConfig
import pl.touk.nussknacker.openapi.enrichers.SwaggerEnrichers
//import pl.touk.nussknacker.openapi.http.backend.DefaultDispatchConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.jdk.CollectionConverters.{asScalaBufferConverter, mapAsScalaMapConverter}

//TODO: move to integration tests or remove "real" RestDB invocation
class RestDBServiceSpec extends FunSuite with BeforeAndAfterAll with Matchers with EitherValues with LazyLogging with ScalaFutures {

  final override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(50, Millis)))

  implicit val metaData: MetaData = MetaData("testProc", StreamMetaData())
  implicit val collector: ServiceInvocationCollector = new ServiceInvocationCollector {
    override def collectWithResponse[A](request: => ToCollect, mockValue: Option[A])(action: => Future[InvocationCollectors.CollectableAction[A]], names: InvocationCollectors.TransmissionNames)(implicit ec: ExecutionContext): Future[A] = action.map(_.result)
  }
  implicit val contextId: ContextId = ContextId("testContextId")

  /*
  private val service: ServiceWithExplicitMethod = {
    val definition = Source.fromInputStream(getClass.getClassLoader.getResourceAsStream("restdb-swagger.json")).mkString
    val securities = Map("apikey" -> ApiKeyConfig("TODO"))

    val categories = List("Default")
    val fixedParameters: Map[String, () => AnyRef] = Map.empty
    val clientConfig = DefaultDispatchConfig().copy(useNative = Some(false))

    val servicesMap = SwaggerEnrichers.enrichersForDefinition(definition, None, securities, categories, fixedParameters, clientConfig, None)

    val service = servicesMap("GET-companies").value.asInstanceOf[ServiceWithExplicitMethod]
    service.open(JobData(metaData, ProcessVersion.empty, DeploymentData.empty))
    service
  }

  ignore("companies") {
    import scala.concurrent.ExecutionContext.Implicits.global

    val valueWithChosenFields = service.invokeService(List("{\"zip\":\"AX6 7KK\"}", null)).futureValue
      .asInstanceOf[java.util.List[_]].asScala.map(_.asInstanceOf[TypedMap].asScala)
      .map(_.filterKeys(List("zip", "city").contains))
    valueWithChosenFields shouldEqual List(Map("zip" -> "AX6 7KK", "city" -> "La Magdeleine"))
  }         */
}