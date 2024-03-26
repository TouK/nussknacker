---
title: Basics
sidebar_position: 1
---
# Model configuration

Model definition is part of a scenario type definition. There can be multiple scenario types in one Nussknacker installation, consequently there will also be multiple model definitions in such a case. 
Check [configuration areas](docs/installation_configuration_guide/Common.md#configuration-areas) to understand where Model configuration should be placed in the Nussknacker configuration. If you deploy to K8s using Nussknacker Helm chart, check [here](../DeploymentManagerConfiguration.md#overriding-configuration-passed-to-runtime) how to supply additional model configuration.

Model defines how to configure [components](/about/GLOSSARY#component) and certain runtime behavior (e.g. error handling) for a given scenario type. Model configuration is processed not only at the Designer but also passed to the execution engine (e.g. Flink), that’s why it’s parsed and processed a bit differently: 

* Some Components can use a special mechanism which resolves and adds additional configuration during deployment, which is then passed to the execution engine. Such configuration is read and resolved only at the Designer. Example: OpenAPI enrichers need to read its definition from external sites - so e.g. Flink cluster does not have to have access to the site with the definition. 
* There is additional set of defaults, taken from `defaultModelConfig.conf` if it exists on the classpath. The standard Nussknacker installation uses the one from [here](https://github.com/TouK/nussknacker/blob/staging/defaultModel/src/main/resources/defaultModelConfig.conf), installations using certain code customizations may use a different one.       
                 
## ClassPath configuration

Nussknacker looks for components and various extensions in jars on the Model classpath, default config [example here](https://github.com/TouK/nussknacker/blob/staging/nussknacker-dist/src/universal/conf/application.conf) to see where classpath can be configured.

By default, the following configuration is used:
```
classPath: ["model/defaultModel.jar", "model/flinkExecutor.jar", "components/flink", "components/common"]
```
Make sure you have all necessary entries properly configured:
- Jar with model - unless you used custom model, this should be `model/defaultModel.jar`
- All jars with additional components, e.g. `"components/flink/flinkBase.jar", "components/flink/flinkKafka.jar"`
- `flinkExecutor.jar` for Flink engine. This contains executor of scenarios in Flink cluster.

Note that as classPath elements you can use:
- full URLs (e.g. "https://repo1.maven.org/maven2/pl/touk/nussknacker/nussknacker-lite-base-components_2.12/1.1.0/nussknacker-lite-base-components_2.12-1.1.0.jar")
- file paths (absolute or relative to Nussknacker installation dir)
- paths to directories (again, absolute or relative) - in this case all files in the directory will be used (including the ones found in subdirectories).

If the given path element in the `classPath` is relative, it should be relative to the path determined by the `$WORKING_DIR ` [environment variable](../../installation/Installation.md#basic-environment-variables).

<!-- TODO 
### Object naming
-->

## Components configuration 

Nussknacker comes with a set of provided components. Some of them (e.g. `filter`, `variable`, aggregations in Flink, `for-each`, `union`) are 
predefined and accessible by default. Others need additional configuration - the most important ones are enrichers, where you have to set e.g. JDBC URL or external service address.

Check Integration documentation for the details on how to configure the following components:
- [OpenAPI](docs/integration/OpenAPI.md) Supports accessing external APIs directly from scenario 
- [SQL](docs/integration/Sql.md)         Supports access to SQL database engines    
- [Machine Learning](docs/integration/MachineLearning.md)         Infers ML models


### Configuration of component providers

Below you can see typical component configuration, each section describes configuration of one component provider.

```
  components {
    sqlHsql {
      providerType: sql
      jdbcUrl: jdbc:hsql...//
      admin/pass
    }
    sqlOracle {
      providerType: sql
      jdbcUrl: jdbc:oracle...//
      admin/pass
    }
    prinzH20 {
      providerType: prinzH20
      h2oLocation:
    }
    prinzMlFlow {
      #this is not needed, as configuration is named just as provider
      #providerType: prinzMLFlow
      mlFlowUrl:
    }
    #we can disable particular component provider, if it's not needed in our installation
    #note: you cannot disable certain basic components like filter, variable, choice and split
    aggregation {
      disabled: true
    }
  }
```

### Configuration of UI attributes of components

In model configuration you can also define some UI attributes of components. This can be useful for tweaking of appearance of generated components (like from OpenAPI), 
in most cases you should not need to defined these settings. The settings you can configure include:
* icons - `icon` property
* documentation - `docsUrl` property
* should component be disabled - `disabled` property
* in which toolbox panel the component should appear (`componentGroup` property)  
* `params` configuration (allows to override default component settings):
  * `editor` - `BoolParameterEditor`, `StringParameterEditor`, `DateParameterEditor` etc. 
  * `validators` - `MandatoryParameterValidator`, `NotBlankParameterValidator`, `LiteralRegexpParameterValidator`
  * `defaultValue`
  * `label`

Example (see [dev application config](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/dev-model/src/main/resources/defaultModelConfig.conf#L18) for more examples):
```
  componentsUiConfig {
    customerService {
      params {
        serviceIdParameter {
            defaultValue: "customerId-10"
            editor: "StringParameterEditor"
            validators: [ 
              {
                type: "LiteralRegExpParameterValidator"
                pattern: "customerId-[0-9]*"
                message: "has to match customer id format"
                description: "really has to match..."
              }
            ]
            label: "Customer id (from CRM!)
        }
      }
      docsUrl: "https://en.wikipedia.org/wiki/Customer_service"
      icon: "icon_file.svg"
    }
  }
```

### Component links

You can add additional links that will be shown in `Components` tab. They can be used e.g. to point to 
custom dashboards with component performance or point to some external system (link to documentation is configured by default). 
The config format is as follows:
```
componentLinks: [
  {
    id: "sourceSystem"
    title: "Source system"
    icon: "/assets/components/CustomNode.svg"
    url: "https://myCustom.com/dataSource/$componentName" 
    supportedComponentTypes: ["openAPIEnricher1"]
  }
]
```
Fields `title`, `icon`, `url` can contain templates: `$componentId` nad `$componentName` which are replaced by component data. Param `supportedComponentTypes` means component's types which can support links.

### Component group mapping

You can override default grouping of basic components in toolbox panels with `componentsGroupMapping` setting. Component names are keys, while values are toolbox panels name (e.g. sources, enrichers etc.)                |

## Scenario's additional properties              

It's possible to add additional properties for scenario. 
They can be used for allowing more detailed scenario information (e.g. pass information about marketing campaign target etc.), 
they can also be used in various Nussknacker extensions: 

Example (see [dev application config](https://github.com/TouK/nussknacker/blob/staging/engine/flink/management/dev-model/src/main/resources/defaultModelConfig.conf#L61) for more examples):

```
additionalPropertiesConfig {
  campaignType: {
    editor: { type: "StringParameterEditor" }
    validators: [ { type: "MandatoryParameterValidator" } ]
    label: "Campaign type"
    defaultValue: "Generic campaign"
  }
  ...
}
```
Configuration is similar to [component configuration](#configuration-of-ui-attributes-of-components)
