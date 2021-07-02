Overview
========
                              
Nussknacker can use services documented with OpenAPI specification.
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
| OpenAPI Type  | OpenAPI Format | Type in Nussknacker |
| ------------- | -------------- | ------------------- |
| boolean       |                | Boolean             |
| string        |                | String              |
| string        | date-time      | LocalDateTime       |
| integer       |                | Long                |
| number        |                | BigDecimal          |
| number        | double         | Double              |
| number        | float          | Double              |
| array         |                | array               |
| map/object    |                | record              |

OpenAPI integration can handle schema references. 
For objects and maps we use `properties` to define structure.
For arrays we use `items` to define type of elements.                    
                                                                                                           

Configuration
=============

Sample configuration:
```
components {
  service1: {
      type: openAPI  
      url = "http://myservice.com/swagger"
      rootUrl = "http://myservice.com/endpoint"
      security {
          apikey {
              type = "apiKey"
              apiKeyValue = "34534asfdasf"
          }
      }
      namePattern: "customer.*"
      allowedMethods: ["GET", "POST"]
  }
}
```

| Parameter      | Required | Default | Description                                                                                                                   |
| ----------     | -------- | ------- | -----------                                                                                                                   |
| url            | true     |         | URL with OpenAPI resource                                                                                                     |
| rootUrl        | false    |         | Base URL of service, can be used to override value from OpenAPI in NAT settings                                               |
| allowedMethods | false    | ["GET"] | Usually only GET services should be used as enrichers are meant to be idempotent and not change data                          |
| namePattern    | false    | .*      | Regexp for filtering operations by operationId (i.e. enricher name)                                                           |
| security       | false    |         | Configuration for [authentication](https://swagger.io/docs/specification/authentication/). Currently only apiKey is supported |