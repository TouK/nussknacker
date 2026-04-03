---

# front matter metadata for Docusaurus
title: OpenAPI Integration Guide
description: Learn how to integrate OpenAPI services into real-time workflows to enable external data calls within decision logic.
sidebar_label: OpenAPI Services
# sidebar_position: 

---

# OpenAPI 


## Overview

[OpenAPI](https://swagger.io) is a specification for machine-readable interface definition for describing, producing,
consuming, and visualizing RESTful web services. Nussknacker can read definition of an OpenAPI interface and
generate an enricher component for each operation defined in the interface (service) specification. 

Nussknacker supports OpenAPI specification versions 2.x and 3.x (note: 2.x is considered deprecated). 

:::note
The functionality allowing to define integrations using UI rather than config files is available in Cloud Version only. If you use OSS edition, check the [Open Source version integrations documentation](/docs/OSS_docs/integration/) for details on how to configure integrations.
:::

## Integration parameters


| Parameter              | Description       -                                                                                                                                     |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| Integration name       | Unique name for the integration                                                                                                                         |
| Spec URL               | URL of the [*OpenAPI interface definition*](https://swagger.io/specification/v3/). It contains definition of the service you want to interact with.     |
| Server URL             | The URL of the service. If not specified, the URL of the service is taken from the *OpenAPI interface definition*.                                      |
| API Key                | API key that will be passed to the service via header, query parameter or cookie, as indicated in the *OpenAPI interface  definition*                   |


## Additional info

Nussknacker applies following rules when mapping OpenAPI operations to enrichers:

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

OpenAPI integration can handle schema references. However, we don't support recursive schemas at the moment. Recursive schema occurrences will be replaced with `Unknown` type.

For objects and maps we use `properties` to define structure.
For arrays, we use `items` to define type of elements.
