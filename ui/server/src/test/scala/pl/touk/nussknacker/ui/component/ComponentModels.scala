package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.process.{Sink, _}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MethodToInvoke, Service}
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object ComponentModelData {
  val categoryMarketing = "Marketing"
  val categoryMarketingTests = "MarketingTest"
  val categoryMarketingSuper = "MarketingSuper"

  val marketingWithoutSuperCategories: List[String] = List(categoryMarketing, categoryMarketingTests).sorted
  val marketingAllCategories: List[String] = List(categoryMarketing, categoryMarketingTests, categoryMarketingSuper).sorted

  val categoryFraud = "Fraud"
  val categoryFraudTests = "FraudTest"
  val categoryFraudSuper = "FraudSuper"

  val fraudWithoutSupperCategories: List[String] = List(categoryFraud, categoryFraudTests).sorted
  val fraudAllCategories: List[String] = List(categoryFraud, categoryFraudTests, categoryFraudSuper).sorted

  val allCategories: List[String] = (marketingAllCategories ++ fraudAllCategories).sorted

  val hiddenFraudCustomerDataEnricherName = "hiddenFraudCustomerDataEnricher"
  val hiddenMarketingCustomerDataEnricherName = "hiddenMarketingCustomerDataEnricher"
  val customerDataEnricherName = "customerDataEnricher"
  val sharedSourceName = "emptySource"
  val sharedSourceV2Name = "emptySource-v2"
  val sharedSinkName = "sendEmail"
  val sharedEnricherName = "sharedEnricher"
  val customStreamName = "customStream"
}

abstract class DefaultStreamingProcessConfigCreator extends EmptyProcessConfigCreator {

  import ComponentModelData._

  protected def admin[T](value: T): WithCategories[T] = WithCategories(value, categoryMarketingSuper, categoryFraudSuper)

  protected def marketing[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, categoryMarketing).withComponentId(componentId)

  protected def marketingAndTests[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, categoryMarketing, categoryMarketingTests).withComponentId(componentId)

  protected def fraud[T](value: T): WithCategories[T] = WithCategories(value, categoryFraud)

  protected def fraudAndTests[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, categoryFraud, categoryFraudTests).withComponentId(componentId)

  protected def all[T](value: T, componentId: Option[String] = None): WithCategories[T] =
    WithCategories(value, categoryMarketing, categoryMarketingTests, categoryMarketingSuper, categoryFraud, categoryFraudTests, categoryFraudSuper).withComponentId(componentId)

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
    sharedSourceName -> marketing(SourceFactory.noParam(EmptySource, Unknown), Some(sharedSourceName)),
    "superSource" -> admin(SourceFactory.noParam(EmptySource, Unknown)),
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    sharedSinkName -> marketing(SinkFactory.noParam(EmptySink), Some(sharedSinkName)),
    "monitor" -> all(SinkFactory.noParam(EmptySink)),
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "fuseBlockService" -> marketingAndTests(EmptyProcessor),
    customerDataEnricherName -> marketing(CustomerDataEnricher),
    sharedEnricherName -> marketing(CustomerDataEnricher, Some(sharedEnricherName)),
    hiddenMarketingCustomerDataEnricherName -> all(CustomerDataEnricher),
  )

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    customStreamName -> marketingAndTests(EmptyCustomStreamTransformer(true, false), Some(customStreamName)),
    "optionalCustomStream" -> marketingAndTests(EmptyCustomStreamTransformer(false, true)),
  )
}

object ComponentFraudTestConfigCreator extends DefaultStreamingProcessConfigCreator {
  import ComponentModelData._
  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
    sharedSourceName -> all(SourceFactory.noParam(EmptySource, Unknown), Some(sharedSourceName)),
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    sharedSinkName -> fraudAndTests(SinkFactory.noParam(EmptySink), Some(sharedSinkName)),
    "secondMonitor" -> all(SinkFactory.noParam(EmptySink)),
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "fuseBlockService" -> fraudAndTests(EmptyProcessor),
    customerDataEnricherName -> fraud(CustomerDataEnricher),
    sharedEnricherName -> fraudAndTests(CustomerDataEnricher, Some(sharedEnricherName)),
    hiddenFraudCustomerDataEnricherName -> all(CustomerDataEnricher),
  )

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    customStreamName -> fraudAndTests(EmptyCustomStreamTransformer(true, false)),
    "optionalCustomStream" -> fraudAndTests(EmptyCustomStreamTransformer(false, true)),
  )
}

object WronglyConfiguredConfigCreator extends DefaultStreamingProcessConfigCreator {
  import ComponentModelData._
  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
    sharedSourceV2Name -> all(SourceFactory.noParam(EmptySource, Unknown), Some(sharedSourceName)),
  )

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    sharedEnricherName -> all(EmptyProcessor, Some(sharedEnricherName)),
    hiddenMarketingCustomerDataEnricherName -> all(CustomerDataEnricher),
  )
}
