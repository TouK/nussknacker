package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import com.azure.core.http.policy._
import com.azure.core.http.{HttpPipeline, HttpPipelineBuilder}
import com.azure.core.util.CoreUtils
import pl.touk.nussknacker.engine.kafka.SchemaRegistryClientKafkaConfig

import java.time.temporal.ChronoUnit
import java.util
import scala.jdk.CollectionConverters._

// It is a copy-paste of part of SchemaRegistryClientBuilder.buildAsyncClient with hardcoded some options like retry policy.
// We need it because we want to keep our enhanced SchemaRegistry client as much compatible
// with this used by Avro(De)Serializer as possible.
object AzureHttpPipelineFactory {

  def createPipeline(config: SchemaRegistryClientKafkaConfig): HttpPipeline = {
    val buildConfiguration = AzureConfigurationFactory.createFromKafkaProperties(config.kafkaProperties)
    val credential = AzureTokenCredentialFactory.createCredential(buildConfiguration)

    val httpLogOptions = new HttpLogOptions()
    val properties = CoreUtils.getProperties("azure-data-schemaregistry.properties")
    val clientName = properties.getOrDefault("name", "UnknownName")
    val clientVersion = properties.getOrDefault("version", "UnknownVersion")

    val policies = new util.ArrayList[HttpPipelinePolicy]
    policies.add(new UserAgentPolicy(CoreUtils.getApplicationId(null, httpLogOptions), clientName, clientVersion, buildConfiguration))
    policies.add(new RequestIdPolicy)
    policies.add(new AddHeadersFromContextPolicy)
    HttpPolicyProviders.addBeforeRetryPolicies(policies)
    policies.add(new RetryPolicy("retry-after-ms", ChronoUnit.MILLIS))
    policies.add(new AddDatePolicy)
    policies.add(new BearerTokenAuthenticationPolicy(credential, "https://eventhubs.azure.net/.default"))
    HttpPolicyProviders.addAfterRetryPolicies(policies)
    policies.add(new HttpLoggingPolicy(httpLogOptions))

    new HttpPipelineBuilder()
      .policies(policies.asScala.toSeq: _*)
      .build()
  }

}
