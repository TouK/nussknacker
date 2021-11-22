package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.process.{Sink, _}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MethodToInvoke, Service}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.ui.component.ComponentFraudTestConfigCreator.fraudAll

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object ComponentModelData {
  val CategoryMarketing = "Marketing"
  val CategoryMarketingTests = "MarketingTest"
  val CategoryMarketingSuper = "MarketingSuper"

  val MarketingWithoutSuperCategories: List[String] = List(CategoryMarketing, CategoryMarketingTests).sorted
  val MarketingAllCategories: List[String] = List(CategoryMarketing, CategoryMarketingTests, CategoryMarketingSuper).sorted

  val CategoryFraud = "Fraud"
  val CategoryFraudTests = "FraudTest"
  val CategoryFraudSuper = "FraudSuper"

  val FraudWithoutSupperCategories: List[String] = List(CategoryFraud, CategoryFraudTests).sorted
  val FraudAllCategories: List[String] = List(CategoryFraud, CategoryFraudTests, CategoryFraudSuper).sorted

  val AllCategories: List[String] = (MarketingAllCategories ++ FraudAllCategories).sorted

  val HiddenFraudCustomerDataEnricherName = "hiddenFraudCustomerDataEnricher"
  val HiddenMarketingCustomerDataEnricherName = "hiddenMarketingCustomerDataEnricher"
  val CustomerDataEnricherName = "customerDataEnricher"
  val SharedSourceName = "emptySource"
  val SharedSourceV2Name = "emptySource-v2"
  val SharedSinkName = "sendEmail"
  val SharedEnricherName = "sharedEnricher"
  val CustomStreamName = "customStream"
  val SuperMarketingSourceName = "superSource"
  val NotSharedSourceName = "notSharedSource"
  val FraudSinkName = "fraudSink"
  val MonitorName = "monitor"
  val FuseBlockServiceName = "fuseBlockService"
  val OptionalCustomStreamName = "optionalCustomStream"
  val SecondMonitorName = "secondMonitor"
}

abstract class DefaultStreamingProcessConfigCreator extends EmptyProcessConfigCreator {

  import ComponentModelData._

  protected def admin[T](value: T): WithCategories[T] = WithCategories(value, CategoryMarketingSuper, CategoryFraudSuper)

  protected def marketing[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, CategoryMarketing).withComponentId(componentId)

  protected def marketingAndTests[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, CategoryMarketing, CategoryMarketingTests).withComponentId(componentId)

  protected def marketingAll[T](value: T): WithCategories[T] = WithCategories(value, CategoryMarketing, CategoryMarketingTests, CategoryMarketingSuper)

  protected def fraud[T](value: T): WithCategories[T] = WithCategories(value, CategoryFraud)

  protected def fraudAndTests[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, CategoryFraud, CategoryFraudTests).withComponentId(componentId)

  protected def fraudAll[T](value: T): WithCategories[T] = WithCategories(value, CategoryFraud, CategoryFraudTests, CategoryFraudSuper)

  protected def all[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, CategoryMarketing, CategoryMarketingTests, CategoryMarketingSuper, CategoryFraud, CategoryFraudTests, CategoryFraudSuper).withComponentId(componentId)

  case object EmptySink extends Sink

  case object EmptySource extends Source

  case object CustomerDataEnricher extends Service with Serializable {
    @MethodToInvoke def invoke()(implicit ec: ExecutionContext): Future[Int] = Future.apply(Random.nextInt())
  }

  case object EmptyProcessor extends Service {
    @MethodToInvoke def invoke(): Future[Unit] = Future.unit
  }

  case class EmptyCustomStreamTransformer(override val canHaveManyInputs: Boolean, override val canBeEnding: Boolean) extends CustomStreamTransformer {
    @MethodToInvoke(returnType = classOf[Void]) def invoke(): Unit = {}
  }
}

object ComponentMarketingTestConfigCreator extends DefaultStreamingProcessConfigCreator {
  import ComponentModelData._

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
    SharedSourceName -> marketing(SourceFactory.noParam(EmptySource, Unknown), Some(SharedSourceName)),
    SuperMarketingSourceName -> admin(SourceFactory.noParam(EmptySource, Unknown)),
    NotSharedSourceName -> marketingAll(SourceFactory.noParam(EmptySource, Unknown)),
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    SharedSinkName -> marketing(SinkFactory.noParam(EmptySink), Some(SharedSinkName)),
    MonitorName -> all(SinkFactory.noParam(EmptySink)),
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    FuseBlockServiceName -> marketingAndTests(EmptyProcessor),
    CustomerDataEnricherName -> marketing(CustomerDataEnricher),
    SharedEnricherName -> marketing(CustomerDataEnricher, Some(SharedEnricherName)),
    HiddenMarketingCustomerDataEnricherName -> all(CustomerDataEnricher),
  )

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    CustomStreamName -> marketingAndTests(EmptyCustomStreamTransformer(true, false), Some(CustomStreamName)),
    OptionalCustomStreamName -> marketingAndTests(EmptyCustomStreamTransformer(false, true)),
  )
}

object ComponentFraudTestConfigCreator extends DefaultStreamingProcessConfigCreator {
  import ComponentModelData._
  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
    SharedSourceName -> all(SourceFactory.noParam(EmptySource, Unknown), Some(SharedSourceName)),
    NotSharedSourceName -> fraudAll(SourceFactory.noParam(EmptySource, Unknown)),
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    SharedSinkName -> fraudAndTests(SinkFactory.noParam(EmptySink), Some(SharedSinkName)),
    FraudSinkName -> fraudAll(SinkFactory.noParam(EmptySink)),
    SecondMonitorName -> all(SinkFactory.noParam(EmptySink)),
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    FuseBlockServiceName -> fraudAndTests(EmptyProcessor),
    CustomerDataEnricherName -> fraud(CustomerDataEnricher),
    SharedEnricherName -> fraudAndTests(CustomerDataEnricher, Some(SharedEnricherName)),
    HiddenFraudCustomerDataEnricherName -> all(CustomerDataEnricher),
  )

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    CustomStreamName -> fraudAndTests(EmptyCustomStreamTransformer(true, false)),
    OptionalCustomStreamName -> fraudAndTests(EmptyCustomStreamTransformer(false, true)),
  )
}

object WronglyConfiguredConfigCreator extends DefaultStreamingProcessConfigCreator {
  import ComponentModelData._
  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
    SharedSourceV2Name -> all(SourceFactory.noParam(EmptySource, Unknown), Some(SharedSourceName)),
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    SharedEnricherName -> all(EmptyProcessor, Some(SharedEnricherName)),
    HiddenMarketingCustomerDataEnricherName -> all(CustomerDataEnricher),
  )
}
