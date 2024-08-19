---
sidebar_position: 3
---

# OpenAPI enrichers

## Overview

Nussknacker can use services documented with OpenAPI interface definition.
We use Swagger to parse OpenAPI, versions 2.x and 3.x are supported
(version 2.x should be considered as deprecated).

Nussknacker applies following rules when mapping OpenAPI services to enrichers:

- Each operation is mapped to one enricher
- If operationId is configured it's used as enricher name, otherwise we create it as concatenation
  of HTTP method, path and parameters (to provide some level of uniqueness)
- Parameters (path, query of header) are used to define enricher parameters
- If specification declares body parameter as object, we expand to parameter list
- We expect operation to define 200/201 response, returned object is the one that is the result of enricher
- We map 404 HTTP code to null value of enricher
- We use `externalDocs.url` to extract documentation link

Table below describes data types that OpenAPI integration handles:

| OpenAPI Type | OpenAPI Format | Type in Nussknacker |
|--------------|----------------|---------------------|
| boolean      |                | Boolean             |
| string       |                | String              |
| string       | date-time      | LocalDateTime       |
| integer      |                | Long                |
| number       |                | BigDecimal          |
| number       | double         | Double              |
| number       | float          | Double              |
| array        |                | array               |
| map/object   |                | record              |

OpenAPI integration can handle schema references. However, we don't support recursive schemas at the moment.
Recursive schema occurrences will be replaced with `Unknown` type.

For objects and maps we use `properties` to define structure.
For arrays, we use `items` to define type of elements.

## Configuration

Open API enricher is configured under `components` configuration key. Check
this [configuration file snippet](../configuration/index.mdx#configuration-areas) to understand the
placement of `components` configuration key in the configuration file.

Sample configuration:

```
components {
  service1: {
      providerType: openAPI  
      url = "http://myservice.com/swagger"
      rootUrl = "http://myservice.com/endpoint"
      security {
          apikeySecuritySchema {
              type = "apiKey"
              apiKeyValue = "34534asfdasf"
          }
      }
      namePattern: "customer.*"
      allowedMethods: ["GET", "POST"]
  }
}
```

| Parameter              | Required | Default | Description                                                                                                                                                        |
|------------------------|----------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| url                    | true     |         | URL of the [*OpenAPI interface definition*](https://swagger.io/specification/v3/). It contains definition of the service you want to interact with.                |
| rootUrl                | false    |         | The URL of the service. If not specified, the URL of the service is taken from the *OpenAPI interface definition*.                                                 |
| allowedMethods         | false    | ["GET"] | Usually only GET services should be used as enrichers are meant to be idempotent and not change data                                                               |
| namePattern            | false    | .*      | Regexp for filtering operations by operationId (i.e. enricher name)                                                                                                |
| security               | false    |         | Configuration for [authentication](https://swagger.io/docs/specification/authentication/) for each `securitySchemas` defined in the *OpenAPI interface definition* |
| security.*.type        | false    |         | Type of security configuration for a given security schema. Currently only `apiKey` is supported                                                                   |
| security.*.apiKeyValue | false    |         | API key that will be passed into the service via header, query parameter or cookie (depending on definition provided in OpenAPI)                                   |

## Operations

You can enable enricher level runtime logging by
`pl.touk.nussknacker.openapi.enrichers.[enricher name]` to `DEBUG`. In particular,
setting `pl.touk.nussknacker.openapi.enrichers`
level to `DEBUG` will turn on logging on all enrichers.

Enricher level logging can be enabled:

- in Flink
  TaskManager [configuration](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/deployment/advanced/logging/)
- in Lite
  runtime [configuration](../configuration/ScenarioDeploymentConfiguration.md#configuring-runtime-logging)
