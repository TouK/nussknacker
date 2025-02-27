package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.internal

import com.azure.core.annotation._
import com.azure.core.http.HttpPipeline
import com.azure.core.http.rest.{Response, RestProxy}
import com.azure.core.util.Context
import com.azure.core.util.serializer.SerializerAdapter
import com.azure.data.schemaregistry.implementation.models.{ErrorException, SchemasGetByIdResponse}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.internal.model.{Schemas, SchemaVersions}
import reactor.core.publisher.Mono

class EnhancedSchemasImpl(
    endpoint: String,
    apiVersion: String,
    httpPipeline: HttpPipeline,
    serializerAdapter: SerializerAdapter
) {

  private val service = RestProxy.create(classOf[SchemasService], httpPipeline, serializerAdapter)

  @Host("https://{endpoint}")
  @ServiceInterface(name = "AzureSchemaRegistryS") trait SchemasService {

    @Get("/$schemaGroups/{groupName}/schemas")
    @ExpectedResponses(Array(200))
    @UnexpectedResponseExceptionType(classOf[ErrorException])
    def getSchemas(
        @HostParam("endpoint") endpoint: String,
        @PathParam("groupName") groupName: String,
        @QueryParam("api-version") apiVersion: String,
        @HeaderParam("Accept") accept: String,
        context: Context
    ): Mono[Response[Schemas]]

    @Get("/$schemaGroups/{groupName}/schemas/{schemaName}")
    @ExpectedResponses(Array(200))
    @UnexpectedResponseExceptionType(classOf[ErrorException])
    def getSchema(
        @HostParam("endpoint") endpoint: String,
        @PathParam("groupName") groupName: String,
        @PathParam("schemaName") schemaName: String,
        @QueryParam("api-version") apiVersion: String,
        @HeaderParam("Accept") accept: String,
        context: Context
    ): Mono[SchemasGetByIdResponse]

    @Get("/$schemaGroups/{groupName}/schemas/{schemaName}/versions")
    @ExpectedResponses(Array(200))
    @UnexpectedResponseExceptionType(classOf[ErrorException])
    def getSchemaVersions(
        @HostParam("endpoint") endpoint: String,
        @PathParam("groupName") groupName: String,
        @PathParam("schemaName") schemaName: String,
        @QueryParam("api-version") apiVersion: String,
        @HeaderParam("Accept") accept: String,
        context: Context
    ): Mono[Response[SchemaVersions]]

  }

  @ServiceMethod(returns = ReturnType.SINGLE)
  def getSchemasWithResponseAsync(groupName: String, context: Context): Mono[Response[Schemas]] = {
    val accept = "application/json"
    service.getSchemas(endpoint, groupName, apiVersion, accept, context)
  }

  @ServiceMethod(returns = ReturnType.SINGLE)
  def getSchemaByNameWithResponseAsync(
      groupName: String,
      schemaName: String,
      context: Context
  ): Mono[SchemasGetByIdResponse] = {
    val accept = "application/json"
    service.getSchema(endpoint, groupName, schemaName, apiVersion, accept, context)
  }

  @ServiceMethod(returns = ReturnType.SINGLE)
  def getVersionsWithResponseAsync(
      groupName: String,
      schemaName: String,
      context: Context
  ): Mono[Response[SchemaVersions]] = {
    val accept = "application/json"
    service.getSchemaVersions(endpoint, groupName, schemaName, apiVersion, accept, context)
  }

}
