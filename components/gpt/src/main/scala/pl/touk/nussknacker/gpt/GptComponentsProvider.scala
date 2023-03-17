package pl.touk.nussknacker.gpt

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.asynchttpclient.DefaultAsyncHttpClient
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.gpt.GptComponentsProvider.GptServiceProvider
import pl.touk.nussknacker.gpt.service.{EchoGtpService, GptService, OpenAIService}

class GptComponentsProvider extends ComponentProvider {
  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val gptConfig = config.as[GptComponentsConfig]
    val serviceProvider = new GptServiceProvider(gptConfig)
    List(
      ComponentDefinition(ChatGptComponent.serviceName, new ChatGptComponent(serviceProvider.provide))
    )
  }

  override def resolveConfigForExecution(config: Config): Config = config
  override def isCompatible(version: NussknackerVersion): Boolean = true
  override def providerName: String = "gpt"

}

object GptComponentsProvider extends LazyLogging {

  val mockGptServicePropertyName = "mockGptService"

  class GptServiceProvider(gptConfig: GptComponentsConfig) {

    private val client = new DefaultAsyncHttpClient
    private val openAIService = new OpenAIService(client, gptConfig)

    def provide(metaData: MetaData): GptService = {
      val shouldMockGptService = metaData.additionalFields.exists { fields =>
        fields.properties.get(mockGptServicePropertyName).contains("true")
      }
      // we allow to mock service via scenario properties because in case if OpenAI service unavailability we want
      // to be able to test other parts of implementation e2e
      if (shouldMockGptService) {
        logger.debug("Using Echo implementation")
        EchoGtpService
      } else {
        logger.debug("Using OpenAI implementation")
        openAIService
      }
    }
  }

}

case class GptComponentsConfig(openaAIApiKey: String, model: String = "gpt-3.5-turbo-0301")