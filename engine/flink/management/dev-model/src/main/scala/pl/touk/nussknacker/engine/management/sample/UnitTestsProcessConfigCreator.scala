package pl.touk.nussknacker.engine.management.sample

import io.circe.generic.JsonCodec
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.api.test.{NewLineSplittedTestDataParser, TestDataParser}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler.SimpleSerializableTimestampAssigner
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{StandardTimestampWatermarkHandler, TimestampWatermarkHandler}
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink
import pl.touk.nussknacker.engine.management.sample.UnitTestsProcessConfigCreator._
import pl.touk.nussknacker.engine.management.sample.helper.DateProcessHelper
import pl.touk.nussknacker.engine.util.service.TimeMeasuringService

import java.nio.charset.StandardCharsets
import java.time.{Duration, LocalDateTime, ZoneOffset}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
 * This config creator is for purpose of unit testing... maybe we should merge it with DevProcessConfigCreator?
 */
object UnitTestsProcessConfigCreator {

  @JsonCodec case class Notification(msisdn: String, notificationType: Int, finalCharge: BigDecimal, tariffId: Long, timestamp: Long) extends DisplayJsonWithEncoder[Notification]

  @JsonCodec case class Transaction(clientId: String, date: LocalDateTime, amount: Int, `type`: String) extends DisplayJsonWithEncoder[Transaction]

  @JsonCodec case class PageVisit(clientId: String, date:LocalDateTime, path: String, ip: String) extends DisplayJsonWithEncoder[PageVisit]

  case class Client(clientId: String, age: Long, isVip: Boolean, country: String)

}

class UnitTestsProcessConfigCreator extends ProcessConfigCreator {

  val fraudDetection = "FraudDetection"

  val recommendations = "Recommendations"

  private def all[T](value: T) = WithCategories(value, fraudDetection, recommendations)

  private def fraud[T](value: T) = WithCategories(value, fraudDetection)

  private def recommendation[T](value: T) = WithCategories(value, recommendations)

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies) = Map()

  override def services(processObjectDependencies: ProcessObjectDependencies) = Map(
    "CustomerDataService" -> all(new CustomerDataService),
    "TariffService"  -> all(new TariffService)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies) = {
    Map(
      "PageVisits" -> recommendation(new RunningSourceFactory[PageVisit]((count: Int) => PageVisit(s"${count % 20}", LocalDateTime.now(),
        s"/products/product${count % 14}", s"10.1.3.${count % 15}"), _.date.toInstant(ZoneOffset.UTC).toEpochMilli,
        line => PageVisit(line(0), LocalDateTime.parse(line(1)), line(2), line(3)))),
      "Transactions" -> fraud(new RunningSourceFactory[Transaction]((count: Int) => Transaction(s"${count % 20}", LocalDateTime.now(),
        count % 34, if (count % 3 == 1) "PREMIUM" else "NORMAL"), _.date.toInstant(ZoneOffset.UTC).toEpochMilli,
        line => Transaction(line(0), LocalDateTime.parse(line(1)), line(2).toInt, line(3)))),
      "Notifications" -> fraud(new RunningSourceFactory[Notification]((count: Int) =>
        Notification(
          msisdn = s"4869312312${count % 9}",
          notificationType = count % 4,
          finalCharge = BigDecimal(count % 5) + BigDecimal((count % 3) / 10d),
          tariffId = count % 5 + 1000,
          timestamp = System.currentTimeMillis()
        ), _.timestamp,
        line => Notification(line(0), line(1).toInt, BigDecimal.apply(line(2)), line(3).toLong, line(4).toLong)))
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies) = Map(
    "ReportFraud" -> fraud(SinkFactory.noParam(EmptySink)),
    "Recommend" -> recommendation(SinkFactory.noParam(EmptySink)),
    "KafkaSink" -> fraud(SinkFactory.noParam(EmptySink))
  )

  override def listeners(processObjectDependencies: ProcessObjectDependencies) = List()

  override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
    val globalProcessVariables = Map(
      "DATE" -> all(DateProcessHelper)
    )
    ExpressionConfig(globalProcessVariables, List.empty)
  }

  override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]] = Map.empty

  override def buildInfo() = Map(
    "process-version" -> "0.1",
    "engine-version" -> "0.1"
  )

  class RunningSourceFactory[T <: DisplayJson :TypeInformation](generate: Int => T, timestamp: SimpleSerializableTimestampAssigner[T], parser: List[String] => T) extends SourceFactory {

    @MethodToInvoke
    def create(@ParamName("ratePerMinute") rate: Int) = {
      new BasicFlinkSource[T] with Serializable with FlinkSourceTestSupport[T] with TestDataGenerator {

        override val typeInformation = implicitly[TypeInformation[T]]

        override def flinkSourceFunction = new SourceFunction[T] {

          var running = true

          var count = 1

          override def cancel() = {
            running = false
          }

          override def run(ctx: SourceContext[T]) = while (running) {
            Thread.sleep(1000 * 60/rate)
            count = count + 1
            ctx.collect(generate(count))
          }
        }

        override val timestampAssigner = Some(StandardTimestampWatermarkHandler.boundedOutOfOrderness(timestamp, Duration.ofMinutes(10)))

        override def generateTestData(size: Int): Array[Byte] = {
          (1 to size).map(generate).map(_.originalDisplay.getOrElse("")).mkString("\n").getBytes(StandardCharsets.UTF_8)
        }

        override def testDataParser: TestDataParser[T] = new NewLineSplittedTestDataParser[T] {
          override def parseElement(testElement: String) = parser(testElement.split('|').toList)
        }

        override def timestampAssignerForTest: Option[TimestampWatermarkHandler[T]] = timestampAssigner
      }
    }

  }

  class TariffService extends Service with TimeMeasuringService with Serializable {

    override protected def serviceName: String = "tariffService"

    @MethodToInvoke
      def invoke(@ParamName("tariffId") tariffId: Long, @ParamName("tariffType") tariffType: TariffType)(implicit ec: ExecutionContext) = {
      measuring {
        val tariffs = Map(
          1000L -> "family tariff",
          1001L -> "company tariff",
          1002L -> "promotion tariff",
          1003L -> "individual tariff",
          1004L -> "business tariff"
        )

        val tariff = tariffs.getOrElse(tariffId, "unknown")
        Thread.sleep(Random.nextInt(50))
        Future.successful(tariff)
      }
    }
  }

  class CustomerDataService extends Service with TimeMeasuringService with Serializable {
    override protected def serviceName: String = "customerDataService"

    @MethodToInvoke
    def invoke(@ParamName("msisdn") msisdn: String)(implicit ec: ExecutionContext): Future[CustomerData] = {
      measuring {
        Thread.sleep(Random.nextInt(100))
        Future.successful(CustomerData(msisdn, "8" + msisdn))
      }
    }
  }

  @JsonCodec case class CustomerData(msisdn: String, pesel: String) extends DisplayJsonWithEncoder[CustomerData]

}

