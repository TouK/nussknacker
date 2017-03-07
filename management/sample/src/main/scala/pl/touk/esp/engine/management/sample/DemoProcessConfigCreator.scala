package pl.touk.esp.engine.management.sample

import java.time.{LocalDateTime, ZoneOffset}

import argonaut.{Argonaut, Json}
import com.typesafe.config.Config
import org.apache.flink.api.common.restartstrategy.RestartStrategies.RestartStrategyConfiguration
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.TimestampAssigner
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import pl.touk.esp.engine.api.{Displayable, MetaData, ParamName, WithFields}
import pl.touk.esp.engine.api.exception.{EspExceptionInfo, ExceptionHandlerFactory}
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, SinkFactory, WithCategories}
import pl.touk.esp.engine.api.test.NewLineSplittedTestDataParser
import pl.touk.esp.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.esp.engine.flink.api.process.{FlinkSource, FlinkSourceFactory}
import pl.touk.esp.engine.flink.util.exception.VerboselyLoggingExceptionHandler

class DemoProcessConfigCreator extends ProcessConfigCreator {

  val fraudDetection = "FraudDetection"

  val recommendations = "Recommendations"

  private def all[T](value: T) = WithCategories(value, fraudDetection, recommendations)

  private def fraud[T](value: T) = WithCategories(value, fraudDetection)

  private def recommendation[T](value: T) = WithCategories(value, recommendations)

  override def customStreamTransformers(config: Config) = Map()

  override def services(config: Config) = Map()

  override def sourceFactories(config: Config) = Map(
    "PageVisits" -> recommendation(new RunningSourceFactory[PageVisit]((count: Int) => PageVisit(s"${count % 20}", LocalDateTime.now(),
      s"/products/product${count % 14}", s"10.1.3.${count % 15}"), _.date.toInstant(ZoneOffset.UTC).toEpochMilli,
      line => PageVisit(line(0), LocalDateTime.parse(line(1)), line(2), line(3)))),
    "Transactions" -> fraud(new RunningSourceFactory[Transaction]((count: Int) => Transaction(s"${count % 20}", LocalDateTime.now(),
      count % 34, if (count % 3 == 1) "PREMIUM" else "NORMAL"), _.date.toInstant(ZoneOffset.UTC).toEpochMilli,
      line => Transaction(line(0), LocalDateTime.parse(line(1)), line(2).toInt, line(3))))
  )

  override def sinkFactories(config: Config) = Map(
    "ReportFraud" -> fraud(SinkFactory.noParam(EmptySink)),
    "Recommend" -> recommendation(SinkFactory.noParam(EmptySink))
  )

  override def listeners(config: Config) = List()

  override def exceptionHandlerFactory(config: Config) = new TopicHandlerFactory

  override def globalProcessVariables(config: Config) = Map(
    "DATE" -> all(DateProcessHelper.getClass)
  )

  override def buildInfo() = Map(
    "process-version" -> "0.1",
    "engine-version" -> "0.1"
  )

  class TopicHandlerFactory extends ExceptionHandlerFactory {

    def create(@ParamName("topic") topic: String, metaData: MetaData) = VerboselyLoggingExceptionHandler(metaData)

  }


  case class Transaction(clientId: String, date: LocalDateTime, amount: Int, `type`: String) extends WithFields {
    override def fields = List(clientId, date, amount, `type`)
    override def display: Json = Argonaut.jObjectFields(
      "clientId" -> Argonaut.jString(clientId),
      "date" -> Argonaut.jString(date.toString),
      "amount" -> Argonaut.jNumber(amount),
      "type" -> Argonaut.jString(`type`)
    )
  }

  case class PageVisit(clientId: String, date:LocalDateTime, path: String, ip: String) extends WithFields {
    override def fields = List(clientId, date, path, ip)
    override def display: Json = Argonaut.jObjectFields(
      "clientId" -> Argonaut.jString(clientId),
      "date" -> Argonaut.jString(date.toString),
      "path" -> Argonaut.jString(path),
      "ip" -> Argonaut.jString(ip)
    )
  }

  case class Client(clientId: String, age: Long, isVip: Boolean, country: String)

  class RunningSourceFactory[T:TypeInformation](generate: Int => T, timestamp: T => Long, parser: List[String] => T) extends FlinkSourceFactory[T] {

    override def testDataParser = Some(new NewLineSplittedTestDataParser[T] {
      override def parseElement(testElement: String) = parser(testElement.split("|").toList)
    })

    def create(@ParamName("ratePerMinute") rate: String /*tutaj z jakiegos powodu musi byc string?*/) = {
      new FlinkSource[T] with Serializable {

        override def typeInformation = implicitly[TypeInformation[T]]

        override def toFlinkSource = new SourceFunction[T] {

          var running = true

          var count = 1

          override def cancel() = {
            running = false
          }

          override def run(ctx: SourceContext[T]) = while (running) {
            Thread.sleep(1000 * 60/rate.toInt)
            count = count + 1
            ctx.collect(generate(count))
          }
        }

        override def timestampAssigner = Some(new BoundedOutOfOrdernessTimestampExtractor[T](Time.minutes(10)) {
          override def extractTimestamp(element: T): Long = timestamp(element)
        })
      }
    }

  }
}
