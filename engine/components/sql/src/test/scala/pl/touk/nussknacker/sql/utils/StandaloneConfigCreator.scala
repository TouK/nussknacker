package pl.touk.nussknacker.sql.utils

import io.circe.Decoder
import io.circe.generic.JsonCodec
import io.circe.generic.semiauto._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.standalone.api.StandaloneSinkWithParameters
import pl.touk.nussknacker.engine.standalone.utils.JsonStandaloneSourceFactory
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.sql.db.pool.DBPoolConfig
import pl.touk.nussknacker.sql.service.DatabaseLookupEnricher

class StandaloneConfigCreator extends EmptyProcessConfigCreator {

  private val Category = "Test"

  implicit val requestDecoder: Decoder[StandaloneRequest] = deriveDecoder[StandaloneRequest]

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = {
    Map(
      "request" -> WithCategories(new JsonStandaloneSourceFactory[StandaloneRequest], Category))
  }

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = {
    val enricherDbPoolConfig = processObjectDependencies.config.as[DBPoolConfig]("sqlEnricherDbPool")
    Map(
      "sql-lookup-enricher" -> WithCategories(new DatabaseLookupEnricher(enricherDbPoolConfig), Category)
    )
  }

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = {
    Map(
      "response" -> WithCategories(ResponseSinkFactory, Category))
  }
}

@JsonCodec case class StandaloneRequest(id: Int)

@JsonCodec case class StandaloneResponse(name: String) extends DisplayJsonWithEncoder[StandaloneResponse]

object ResponseSinkFactory extends SinkFactory {
  override def requiresOutput: Boolean = false

  @MethodToInvoke
  def invoke(@ParamName("name") name: LazyParameter[String]): Sink = new ResponseSink(name)
}

class ResponseSink(nameParam: LazyParameter[String]) extends StandaloneSinkWithParameters {
  override def prepareResponse(implicit evaluateLazyParameter: LazyParameterInterpreter): LazyParameter[AnyRef] =
    nameParam.map(name => StandaloneResponse(name))

  override def testDataOutput: Option[Any => String] = Some({ case response: StandaloneResponse => response.asJson.spaces2 })
}
