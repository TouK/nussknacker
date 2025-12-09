---
title: OpenAPI Integration Guide
description: Learn how to integrate OpenAPI services into real-time workflows to enable external data calls within decision logic.
sidebar_label: OpenAPI Enrichers
sidebar_position: 3
---

# OpenAPI enrichers

## Overview

Nussknacker can use services documented with OpenAPI interface definition.
We use Swagger to parse OpenAPI, versions 2.x and 3.x are supported
(version 2.x should be considered as deprecated).

Nussknacker applies following rules when mapping OpenAPI services to enrichers:

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
| integer      | int32          | Integer             |
| integer      | int64          | Long                |
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

```hocon
components {
  service1: {
    providerType: "openAPI"
    url: "http://myservice.com/swagger"
    rootUrl: "http://myservice.com/endpoint"
    security {
      apiKeySecuritySchema {
        type: "apiKey"
        apiKeyValue: "34534asfdasf"
      }
    }
    namePattern: "customer.*"
    allowedMethods: ["GET", "POST"]
  }
}
```

| Parameter              | Required | Default | Description                                                                                                                                                                                                                                                                                                        |
|------------------------|----------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| url                    | true     |         | URL of the [*OpenAPI interface definition*](https://swagger.io/specification/v3/). It contains definition of the service you want to interact with.                                                                                                                                                                |
| rootUrl                | false    |         | The URL of the service. If not specified, the URL of the service is taken from the *OpenAPI interface definition*.                                                                                                                                                                                                 |
| allowedMethods         | false    | ["GET"] | Usually only GET services should be used as enrichers are meant to be idempotent and not change data                                                                                                                                                                                                               |
| namePattern            | false    | .*      | Regexp for filtering operations by operationId or by created service name (concatenation of HTTP method, path and parameters)                                                                                                                                                                                      |
| security               | false    |         | Configuration for [authentication](https://swagger.io/docs/specification/authentication/) for each `securitySchemes` defined in the *OpenAPI interface definition*                                                                                                                                                 |
| security.*.type        | false    |         | Type of security configuration for a given security schema. Currently `apiKey` and `basicAuth` are supported                                                                                                                                                                                                       |
| security.*.apiKeyValue | false    |         | API key that will be passed into the service via header, query parameter or cookie (depending on definition provided in OpenAPI)                                                                                                                                                                                   |
| security.*.username    | false    |         | Username used for HTTP Basic authentication when the selected security type is `basicAuth`                                                                                                                                                                                                                         |
| security.*.password    | false    |         | Password used for HTTP Basic authentication when the selected security type is `basicAuth`                                                                                                                                                                                                                         |
| secrets                | false    |         | Configuration for list of [authentication](https://swagger.io/docs/specification/authentication/) which matches all schemas `securitySchemas` of its type in the *OpenAPI interface definition*. This config entry expects a list with elements with the same structure as values in `security` object (see above) |

### Configuring authentication

Two types of authentication are supported:
1. API key
```hocon
{
  type: "apiKey"
  apiKeyValue: "secretValue"
}
```
2. Basic auth
```hocon
{
  type: "basicAuth"
  username: "myUsername"
  password: "secretPassword"
}
```

Authentication can be configured in two ways:

1. Through the `secrets` parameter - by targeting all `securitySchemes` that match used security type (either all Basic
   Authentication schemes or all API key schemes)
2. Through the `security` parameter - by targeting a specific `securityScheme` by its name
    - A matching `security` entry will take precedence over a matching `secrets` entry

#### Example authentication configurations

A minimal OpenAPI definition of a `GET` endpoint requiring passing `apiKey` in header may look like this:

```yaml
openapi: "3.0.0"
info:
  title: Example
  version: 1.0.0
servers:
  - url: http://myservice.com
paths:
  /headerPath:
    get:
      security:
        - apiKeyInHeader: [ ]
      responses:
        '200':
          description: OK
components:
  schemas: { }
  securitySchemes:
    apiKeyInHeader:
      type: apiKey
      name: keyHeader
      in: header
```

Let's suppose the API key is "secretValue".

##### Common secret configuration
If the definition uses only one authentication scheme of any type, the simplest configuration is through `secrets`. This
way, the `securityScheme` name does not need to be specified.

This way is also suitable if there are multiple different `apiKey` schemes, but the actual API key value is the same.
The configured key will be used for all of them.

```hocon
components {
  exampleService: {
    providerType: "openAPI"
    url: "http://myservice.com/swagger"
    secrets: [{
      type: "apiKey"
      apiKeyValue: "secretValue"
    }]
  }
}
```

##### Single scheme configuration 
Configuring specific authentication per `securityScheme` requires setting under `security` the scheme name as the key
and the type and authentication config as the value.

```hocon
components {
  exampleService: {
    providerType: "openAPI"
    url: "http://myservice.com/swagger"
    security {
      apiKeyInHeader {
        type: "apiKey"
        apiKeyValue: "34534asfdasf"
      }
    }
  }
}
```

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
