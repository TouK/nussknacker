package pl.touk.nussknacker.ui.definition.component

import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MethodToInvoke, Service}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object ComponentModelData {
  val CategoryMarketing = "Marketing"
  val CategoryFraud     = "Fraud"

  val AllCategories: List[String] = List(CategoryMarketing, CategoryFraud).sorted

  val ProcessingTypeStreaming = "streaming"
  val ProcessingTypeFraud     = "fraud"

  val HiddenFraudCustomerDataEnricherName     = "hiddenFraudCustomerDataEnricher"
  val HiddenMarketingCustomerDataEnricherName = "hiddenMarketingCustomerDataEnricher"
  val CustomerDataEnricherName                = "customerDataEnricher"
  val SharedSourceName                        = "emptySource"
  val SharedSourceV2Name                      = "emptySource-v2"
  val SharedSinkName                          = "sendEmail"
  val SharedEnricherName                      = "sharedEnricher"
  val CustomStreamName                        = "customStream"
  val SuperMarketingSourceName                = "superSource"
  val NotSharedSourceName                     = "notSharedSource"
  val FraudSinkName                           = "fraudSink"
  val MonitorName                             = "monitor"
  val FuseBlockServiceName                    = "fuseBlockService"
  val OptionalCustomStreamName                = "optionalCustomStream"
  val SecondMonitorName                       = "secondMonitor"
}

abstract class DefaultStreamingProcessConfigCreator extends EmptyProcessConfigCreator {

  import ComponentModelData._

  protected def marketing[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, CategoryMarketing).withComponentId(componentId)

  protected def fraud[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, CategoryFraud).withComponentId(componentId)

  protected def all[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories.anyCategory(value).withComponentId(componentId)

  case object EmptySink extends Sink

  case object EmptySource extends Source

  case object CustomerDataEnricher extends Service with Serializable {
    @MethodToInvoke def invoke()(implicit ec: ExecutionContext): Future[Int] = Future.apply(Random.nextInt())
  }

  case object EmptyProcessor extends Service {
    @MethodToInvoke def invoke(): Future[Unit] = Future.unit
  }

  sealed case class EmptyCustomStreamTransformer(
      override val canBeEnding: Boolean
  ) extends CustomStreamTransformer {
    @MethodToInvoke(returnType = classOf[Void]) def invoke(): Unit = {}
  }

}

object ComponentMarketingTestConfigCreator extends DefaultStreamingProcessConfigCreator {
  import ComponentModelData._

  override def sourceFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = Map(
    SharedSourceName -> all(SourceFactory.noParamUnboundedStreamFactory(EmptySource, Unknown), Some(SharedSourceName)),
    SuperMarketingSourceName -> marketing(SourceFactory.noParamUnboundedStreamFactory(EmptySource, Unknown)),
    NotSharedSourceName      -> marketing(SourceFactory.noParamUnboundedStreamFactory(EmptySource, Unknown)),
  )

  override def sinkFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] = Map(
    SharedSinkName -> all(SinkFactory.noParam(EmptySink), Some(SharedSinkName)),
    MonitorName    -> marketing(SinkFactory.noParam(EmptySink)),
  )

  override def services(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
    Map(
      FuseBlockServiceName                    -> marketing(EmptyProcessor),
      CustomerDataEnricherName                -> marketing(CustomerDataEnricher),
      SharedEnricherName                      -> all(CustomerDataEnricher, Some(SharedEnricherName)),
      HiddenMarketingCustomerDataEnricherName -> all(CustomerDataEnricher),
    )

  override def customStreamTransformers(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    CustomStreamName         -> all(EmptyCustomStreamTransformer(false), Some(CustomStreamName)),
    OptionalCustomStreamName -> marketing(EmptyCustomStreamTransformer(true)),
  )

}

object ComponentFraudTestConfigCreator extends DefaultStreamingProcessConfigCreator {
  import ComponentModelData._

  override def sourceFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = Map(
    SharedSourceName -> all(SourceFactory.noParamUnboundedStreamFactory(EmptySource, Unknown), Some(SharedSourceName)),
    NotSharedSourceName -> fraud(SourceFactory.noParamUnboundedStreamFactory(EmptySource, Unknown)),
  )

  override def sinkFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] = Map(
    SharedSinkName    -> all(SinkFactory.noParam(EmptySink), Some(SharedSinkName)),
    FraudSinkName     -> fraud(SinkFactory.noParam(EmptySink)),
    SecondMonitorName -> all(SinkFactory.noParam(EmptySink)),
  )

  override def services(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
    Map(
      FuseBlockServiceName                -> fraud(EmptyProcessor),
      CustomerDataEnricherName            -> fraud(CustomerDataEnricher),
      SharedEnricherName                  -> all(CustomerDataEnricher, Some(SharedEnricherName)),
      HiddenFraudCustomerDataEnricherName -> all(CustomerDataEnricher),
    )

  override def customStreamTransformers(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    CustomStreamName         -> fraud(EmptyCustomStreamTransformer(false)),
    OptionalCustomStreamName -> fraud(EmptyCustomStreamTransformer(true)),
  )

}

object WronglyConfiguredConfigCreator extends DefaultStreamingProcessConfigCreator {
  import ComponentModelData._

  override def sourceFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = Map(
    SharedSourceV2Name -> all(
      SourceFactory.noParamUnboundedStreamFactory(EmptySource, Unknown),
      Some(SharedSourceName)
    ),
  )

  override def services(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
    Map(
      SharedEnricherName                      -> all(EmptyProcessor, Some(SharedEnricherName)),
      HiddenMarketingCustomerDataEnricherName -> marketing(CustomerDataEnricher),
    )

}
