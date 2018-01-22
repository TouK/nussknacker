package pl.touk.nussknacker.engine.util.service.query

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.NodeContext
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.{ProcessObjectDefinitionExtractor, ServiceInvoker}

import scala.concurrent.{ExecutionContext, Future}

// TODO: Processes using Flink's RuntimeContex, ex. metrics throws NPE, but in another thread, so service works.
class ServiceQuery(modelData: ModelData) {

  import ServiceQuery._
  import pl.touk.nussknacker.engine.util.Implicits._

  private val serviceMethodMap: Map[String, ObjectWithMethodDef] =
    modelData.withThisAsContextClassLoader {
      val servicesMap = modelData.configCreator.services(modelData.processConfig)
      def serviceMethod(factory: WithCategories[Service]) = {
        ObjectWithMethodDef(factory, ProcessObjectDefinitionExtractor.service)
      }

      servicesMap.mapValuesNow(serviceMethod)
    }

  def invoke(serviceName: String, serviceParameters: (String, Any)*)
            (implicit executionContext: ExecutionContext, metaData: MetaData): Future[Any] = {
    val methodDef: ObjectWithMethodDef = serviceMethodMap
      .getOrElse(serviceName, throw ServiceNotFoundException(serviceName))
    val lifecycle = closableService(methodDef)
    lifecycle.open()
    val f = ServiceInvoker(methodDef).invoke(serviceParameters.toMap, dummyNodeContext)
    closeOnComplete(f, lifecycle)
  }

  private def closeOnComplete(f: Future[Any], lifecycle: Lifecycle)
                             (implicit executionContext: ExecutionContext) = {
    f.onComplete {
      _ => lifecycle.close()
    }
    f
  }

  private def closableService(methodDef: ObjectWithMethodDef): Lifecycle = {
    methodDef match {
      case ObjectWithMethodDef(lifecycle: Lifecycle, _, _) => lifecycle
      case _ => throw new IllegalArgumentException
    }
  }
}

object ServiceQuery {

  private val dummyNodeContext = NodeContext(
    contextId = "dummyContextId",
    nodeId = "dummyNodeId",
    ref = "dummyRef"
  )

  case class ServiceNotFoundException(serviceName: String) extends RuntimeException(s"service $serviceName not found")

  object Implicits {
    implicit val metaData: MetaData = MetaData(
      id = "testProcess",
      typeSpecificData = StandaloneMetaData(None),
      additionalFields = None
    )
  }

}
