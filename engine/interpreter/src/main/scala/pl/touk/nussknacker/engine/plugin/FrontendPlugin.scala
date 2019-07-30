package pl.touk.nussknacker.engine.plugin

import argonaut.Json
import pl.touk.nussknacker.engine.util.plugins.Plugin

/*
  WARNING: API may change in the future

  Frontend extension. Plugin is loaded from global classloader.
  Each processing type can have different configuration, passed to frontend (see PluginManager.js), for example:
  FrontendPlugin may represent custom expression creator, based on query builder in this case configuration would
  represent a list of accessible fields.

 */
trait FrontendPlugin extends Plugin {

  final override type ProcessingTypeSpecific = Json

  //this should be list of absolute urls with javascript, e.g. JS library from CDN
  def externalResources: List[String]

  //each resource should be js script. TODO: loading other resources, like images etc.
  def internalResources: Map[String, Array[Byte]]

}