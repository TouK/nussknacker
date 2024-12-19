# ComponentProvider API
              
Check [nussknacker-sample-components](https://github.com/touk/nussknacker-sample-components) project for self-contained samples of custom Components.

# Components implementation
                 
Component is defined by two parts:
- specification
- implementation

Specification defines what are parameters of the component, and what are the results - i.e. how it transforms
the Context. 
                                 
For simple components, the specification is derived from implementing method with the help of annotations. 

## Specification
                                                                                             
### @MethodToInvoke

This is the simplest way to define component. Annotations are used to bind specification and implementation. 
The main limitations of this method are:
- All parameters have to be fixed. 
- Component can only add value of fixed type to ValidationContext. 
    
Examples:                                      
- [Service](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/dev-model/src/main/scala/pl/touk/nussknacker/engine/management/sample/service/MultipleParamsService.scala#L10),
- [SourceFactory](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components/base/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/SampleGeneratorSourceFactory.scala#L51)

### ContextTransformation

This method uses annotations to define parameters, but ValidationContext transformation is defined with code. This allows
to define component which e.g. returns value with type based on input parameters (good example is database lookup enricher - 
the type of enrichment is based on the name of the table).
The main limitation of this method is that the parameters have to be fixed. 
                      
Examples:
- [CustomStreamTransformer](https://github.com/TouK/nussknacker/blob/staging/engine/flink/components/base/src/main/scala/pl/touk/nussknacker/engine/flink/util/transformer/TransformStateTransformer.scala#L40)

### DynamicComponent

This is the most powerful way to define components. It allows for:
- Arbitrary ValidationContext transformations
- Dynamic parameters. In particular:
    - Parameters may depend on configuration. E.g., in OpenAPI enricher URL with definition is passed in configuration,
      the parameters are generated on the base of this definition. 
    - Parameters may depend on each other. Good example is Kafka/Schema Registry Sink. The first parameter determines the target topic and its schema, which 
      defines the rest of the parameters
                                  
Examples:
- [CustomStreamTransformer](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/dev-model/src/main/scala/pl/touk/nussknacker/engine/management/sample/transformer/LastVariableFilterTransformer.scala)

### Lazy parameters

Most of the components are in fact implemented as factories of (e.g. SourceFactory, SinkFactory etc.)
To construct real source or sink we need to know values of some parameters (e.g. name of the topic, URL of service).

Other parameters allow the engine to construct the invocation (e.g. they represent the value of the kafka message), 
so their value is not know when source/sink object (represented e.g. by Kafka consumer/producer) is created.
The later kind of parameters is represented by `LazyParameter` trait. 
Scenario author usually uses expressions containing `#input` variable etc. to define such parameter.
During creation of component implementation
their value is not known, but their exact type is known - as during scenario compilation the expression computing the value is compiled and typechecked.

Please note that "eager" parameters - computed before creating implementation of the component - can also be represented with
non-constant expressions in the Designer - they just have to be 'fixed' - e.g. they cannot contain variables such as `#input` etc. 
As an example, consider simple Kafka sink, which has two parameters:
- `topic` - eager parameter
- `message` - lazy parameter
`topic` value can be e.g. `#meta.processName`, but it is not possible to write `#input.value` there.


To compute the value of `LazyParameter` we need:
- an instance of `LazyParameterInterpreter`, 
which is provided by NU engine implementation - e.g. for Flink components it can be obtained 
`FlinkCustomNodeContext.lazyParamterHelper`.
- `Context` object which stores current variable values. 

## Implementation

Implementation of most of the component types depends on the engine which will execute the scenario. 
See following documentation on engine-specific details:
- [Flink components](FlinkComponents.md) section.
- [Lite components](LiteComponents.md) section.

## Enrichers
                         
Enrichers do not require engine-specific implementation. 
They can be implemented in two flavours:
- standard [Service](https://github.com/TouK/nussknacker/blob/staging/components-api/src/main/scala/pl/touk/nussknacker/engine/api/Service.scala) - configured with `@MethodToInvoke` - suitable for simple enrichments with fixed structure.  
- [EagerService](https://github.com/TouK/nussknacker/blob/staging/components-api/src/main/scala/pl/touk/nussknacker/engine/api/Service.scala). Use this method if you want to have dynamic parameters or output type. Please see [EagerServiceWithStaticParameters](https://github.com/TouK/nussknacker/blob/staging/utils/components-utils/src/main/scala/pl/touk/nussknacker/engine/util/service/EagerServiceWithStaticParameters.scala) for helper traits. 

Examples:
- [Service](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/dev-model/src/main/scala/pl/touk/nussknacker/engine/management/sample/service/MultipleParamsService.scala#L8),
- [EagerService](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/dev-model/src/main/scala/pl/touk/nussknacker/engine/management/sample/service/CustomValidatedService.scala)
                  
### Parameters of enrichers

Please note that Parameters in standard Service and EagerService are interpreted a bit differently:
- in standard Service, each Parameter is evaluated during Service invocation. All variables (e.g. `#input`, enrichment outputs) can
  be used in their expressions
- in EagerService (in particular, implementations of `EagerServiceWithStaticParameters` or `EagerServiceWithStaticParametersAndReturnType`) you can define
  - LazyParameters, which are evaluated during Service invocation - their expressions can use all available variables,
  - standard parameters (e.g. of type `String`) that are evaluated during Service creation. Their expressions can only use global variables
    (e.g `#META`, but not `#input`). They can be used e.g. to define return type of Service, or its Parameters.
