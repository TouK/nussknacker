import _ from "lodash";
import {API_URL} from "../config";
import React from "react";
import ReactDOM from 'react-dom';
import HttpService from '../http/HttpService'

/*
  Javascript plugin object has following structure:
  {
    canCreateExpression: (fieldName, language) -> boolean
    createExpression: (onValueChange, fieldName, expressionObj, config) -> Component
  }
  In the future other methods may be added to provide more customization hooks

  We assume that one of scripts in resources will invoke PluginManager.invoke(name, plugin)
  TODO: how to make it more automatic?

 */

window.PluginManager = {

  plugins: [],

  //map pluginName -> processingType -> config
  configs: {},

  init(plugins) {
    this.configs = _.mapValues(plugins, pluginConfig => pluginConfig.configs);

    //TODO: figure out how to do it via webpack...
    window.React = React;
    window.ReactDOM = ReactDOM;

    plugins.forEach(plugin => {
      plugin.externalResources.forEach(url => {
        _initResource(url)
      });
      plugin.internalResources.forEach(pluginFile => {
        _initResource(`${API_URL}/plugins/${plugin.name}/resources/${pluginFile}`)
      });
    });
  },

  _initResource(url) {
    const script = document.createElement("script");
    script.async = true;
    script.src = url;
    script.onerror = (error) => HttpService.addError("Failed to load script", error, false)
    //TODO: provide some onload behaviour?

    document.body.appendChild(script);
  },

  //this is invoked by plugin itself in script
  register(name, plugin) {
    console.log("Initializing plugin", name, this);
    this.plugins.push({
      name: name,
      pluginObject: plugin,
    });
    console.log("Plugins: ", this.plugins.map(n => n.name))
  },

  createExpression(onValueChange, fieldName, expressionObj, processingType) {
    const pluginToCreate = this.plugins
          .find(plugin => plugin.pluginObject.canCreateExpression(fieldName, expressionObj.language));

    const config = pluginToCreate ? _.get(this.configs, pluginToCreate.name + "." + processingType)
    //TODO: variables + type information? Or whole reducers?
    return pluginToCreate && config ? pluginToCreate.pluginObject.createExpression(onValueChange, fieldName, expressionObj, config) : null;
  }

};
export default window.PluginManager;