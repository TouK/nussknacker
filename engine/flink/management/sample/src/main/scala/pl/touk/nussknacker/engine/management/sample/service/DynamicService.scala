package pl.touk.nussknacker.engine.management.sample.service

import java.io.File

import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.api.{ContextId, MetaData}
import pl.touk.nussknacker.engine.api.definition.{Parameter, ServiceWithExplicitMethod}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Properties
import scala.collection.JavaConverters._

//this is to simulate model reloading - we read parameters from file
//WARN: this service is Thread unsafe for reload - currently used only in @see BaseFlowTest!
class DynamicService extends ServiceWithExplicitMethod {

  private val fileWithDefinition = new File(Properties.tmpDir, "nk-dynamic-params.lst")

  override def invokeService(params: List[AnyRef])
                            (implicit ec: ExecutionContext, collector: ServiceInvocationCollector, metaData: MetaData, contextId: ContextId): Future[AnyRef] = {
    val toCollect = params.mkString(",")
    val res = ().asInstanceOf[AnyRef]
    collector.collect(toCollect, Some(res))(Future.successful(res))
  }

  //we load parameters only *once* per service creation
  override val parameterDefinition: List[Parameter] = {
    val paramNames = if (fileWithDefinition.exists()) {
      FileUtils.readLines(fileWithDefinition).asScala.toList
    } else Nil
    paramNames.map(name => Parameter[String](name))
  }

  override def returnType: typing.TypingResult = Typed[Unit]
}