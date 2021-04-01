package pl.touk.nussknacker.engine.api.component

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, SingleNodeConfig}


/*
  Service, SourceFactory, SinkFactory, CustomStreamTransformer
 */
trait Component


/*
  componentProviders {
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
      //this is not needed, as configuration is named just as provider
      //providerType: prinzMLFlow
      mlFlowUrl:
    }
    openapiService1 {
      providerType: openapi
      url: http://host1
    }
    openapiService2 {
      providerType: openapi
      url: http://host2
      componentsConfig {
        //we want to ignore particular service here
        serviceA {
          disabled: true
        }
      }
    }
    //not needed in our domain
    aggregation {
      disabled: false
    }
    configurableFilter {
      componentsConfig: {
        filterWithA {
          component: customFilter
          customProperty1: A
        },
        filterWithB {
          component: customFilter
          customProperty1: B
        }
      }
    }
  }
 */
case class ComponentProviderConfig(providerType: Option[String],
                                   disabled: Boolean = false,
                                    //TODO: do we need it?
                                    elementPrefix: Option[String],
                                    componentsConfig: Map[String, ComponentConfig],
                                    categories: List[String],
                                    config: Config)

case class ComponentConfig(component: Option[String],
                            disabled: Boolean = false,
                            config: Config,
                           //TODO: do we want it here??
                            uiConfig: Option[SingleNodeConfig])


//TODO: figures out how to handle versioning, use e.g. semver4j?
case class NussknackerVersion(value: String)

object ComponentData {

  def simple(name: String, component: Component): ComponentDefinition = ComponentDefinition(name, enabledByDefault = true, (_, c) => (component, c))

}

//In some cases Nussknacker config admin can be 
case class ComponentDefinition(name: String, enabledByDefault: Boolean,
                         //we return Option[SingleNodeConfig], to make it possible to configure node config (e.g defaults) for
                         create: (Config, Option[SingleNodeConfig]) => (Component, Option[SingleNodeConfig]))

//case class Component

/**
  * Implementations should be registered with ServiceLoader mechanism. Each provider can be configured multiple times
  * (e.g. differnent DBs, different OpenAPI registrars and so on.
  */
trait ComponentProvider {

  def providerName: String

  //for e.g. OpenAPI we don't want to resolve swagger on Flink (it can be in different network location, or have lower HA guarantees)
  def resolveConfigForExecution(config: Config): Config

  def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition]

  def isCompatible(version: NussknackerVersion): Boolean

}







