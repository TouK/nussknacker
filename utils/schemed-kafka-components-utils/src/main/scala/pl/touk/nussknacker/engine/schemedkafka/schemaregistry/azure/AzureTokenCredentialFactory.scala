package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import com.azure.core.credential.TokenCredential
import com.azure.core.util.{Configuration, ConfigurationPropertyBuilder}
import com.azure.identity.{ChainedTokenCredentialBuilder, ClientSecretCredentialBuilder, DefaultAzureCredentialBuilder}
import scala.jdk.CollectionConverters._

// This class in the first place uses properties from configuration and when they are absent, fallback to
// default credentials chain which check many other sources of credentials:
// https://github.com/Azure/azure-sdk-for-java/wiki/Azure-Identity-Examples#authenticating-with-defaultazurecredential
object AzureTokenCredentialFactory {

  def createCredential(configuration: Configuration): TokenCredential = {
    def getConfigValueOpt(name: String) = Option(configuration.get(ConfigurationPropertyBuilder.ofString(name).build()))
    (getConfigValueOpt(Configuration.PROPERTY_AZURE_TENANT_ID),
      getConfigValueOpt(Configuration.PROPERTY_AZURE_CLIENT_ID),
      getConfigValueOpt(Configuration.PROPERTY_AZURE_CLIENT_SECRET)) match {
      case (Some(tenantId), Some(clientId), Some(clientSecret)) =>
        new ClientSecretCredentialBuilder()
          .tenantId(tenantId)
          .clientId(clientId)
          .clientSecret(clientSecret)
          .build()
      case _ =>
        new DefaultAzureCredentialBuilder().configuration(configuration).build()
    }
  }

}
