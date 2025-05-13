package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.internal

import com.azure.core.credential.TokenCredential
import com.azure.core.util.{Configuration, ConfigurationPropertyBuilder}
import com.azure.identity.{ChainedTokenCredentialBuilder, ClientSecretCredentialBuilder, DefaultAzureCredentialBuilder}
import com.typesafe.scalalogging.LazyLogging

import scala.jdk.CollectionConverters._

// This class create credential chain that in the first place uses properties from configuration
// and when they are absent, fallback to default credentials chain which check many source of credentials:
// https://github.com/Azure/azure-sdk-for-java/wiki/Azure-Identity-Examples#authenticating-with-defaultazurecredential
object AzureTokenCredentialFactory extends LazyLogging {

  def createCredential(configuration: Configuration): TokenCredential = {
    def getConfigValueOpt(name: String) = Option(configuration.get(ConfigurationPropertyBuilder.ofString(name).build()))
    val clientSecretCredentialOpt = (
      getConfigValueOpt(Configuration.PROPERTY_AZURE_TENANT_ID),
      getConfigValueOpt(Configuration.PROPERTY_AZURE_CLIENT_ID),
      getConfigValueOpt(Configuration.PROPERTY_AZURE_CLIENT_SECRET)
    ) match {
      case (Some(tenantId), Some(clientId), Some(clientSecret)) =>
        Some(
          new ClientSecretCredentialBuilder()
            .tenantId(tenantId)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .build()
        )
      case _ =>
        None
    }
    val chain =
      clientSecretCredentialOpt.toList :+ new DefaultAzureCredentialBuilder().configuration(configuration).build()
    new ChainedTokenCredentialBuilder().addAll(chain.asJava).build
  }

}
