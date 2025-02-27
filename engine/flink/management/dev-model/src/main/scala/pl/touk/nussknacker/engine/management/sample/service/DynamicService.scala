package pl.touk.nussknacker.engine.management.sample.service

import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.api.{ContextId, MetaData}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.util.service.EagerServiceWithStaticParametersAndReturnType

import java.io.File
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.Properties

//this is to simulate model reloading - we read parameters from file
//WARN: this service is Thread unsafe for reload - currently used only in @see BaseFlowTest!
class DynamicService extends EagerServiceWithStaticParametersAndReturnType {

  private val fileWithDefinition = new File(Properties.tmpDir, "nk-dynamic-params.lst")

  override def invoke(eagerParameters: Map[ParameterName, Any])(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      contextId: ContextId,
      metaData: MetaData,
      componentUseCase: ComponentUseCase
  ): Future[AnyRef] = {
    val toCollect = eagerParameters.values.mkString(",")
    val res       = ().asInstanceOf[AnyRef]
    collector.collect(toCollect, Some(res))(Future.successful(res))
  }

  // we load parameters only *once* per service creation
  override val parameters: List[Parameter] = {
    val paramNames = if (fileWithDefinition.exists()) {
      FileUtils.readLines(fileWithDefinition, StandardCharsets.UTF_8).asScala.toList
    } else Nil
    paramNames.map(name => Parameter[String](ParameterName(name)).copy(isLazyParameter = true))
  }

  override def returnType: typing.TypingResult = Typed[Unit]
}
