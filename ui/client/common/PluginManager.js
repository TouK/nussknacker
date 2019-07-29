import _ from "lodash";
import {API_URL} from "../config";
import React from "react";
import ReactDOM from 'react-dom';

/*
  Plugin has following structure:
  {
    canCreateExpression: (fieldName, language) -> boolean
    createExpression: (onValueChange, fieldName, expressionObj, config) -> Component
  }
 */

window.PluginManager = {

  plugins: [],

  configs: {},

  init(plugins) {
    console.log("Init with", this.configs);
    this.configs = _.mapValues(_.keyBy(plugins, 'name'), 'frontendConfig')

    //TODO: figure out how to do it via webpack...
    window.React = React;
    window.ReactDOM = ReactDOM;


    plugins.forEach(plugin => {
      plugin.frontendResourceNames.forEach(pluginFile => {
        const script = document.createElement("script");
        script.async = true;
        script.src = `${API_URL}/plugins/${plugin.name}/resources/${pluginFile}`;
        document.body.appendChild(script);
      });
    });
  },

  //this is invoked by plugin itself in script
  register(name, plugin) {
    console.log("Initializing plugin", name, this);
    this.plugins.push({
      name: name,
      pluginObject: plugin,
      config: this.configs[name]
    });
    console.log("Plugins: ", this.plugins.map(n => n.name))
  },

  createExpression(onValueChange, fieldName, expressionObj) {
    const pluginToCreate = this.plugins
          .find(plugin => plugin.pluginObject.canCreateExpression(fieldName, expressionObj.language));

    //TODO: variables + type information? Or whole reducers?
    return pluginToCreate ? pluginToCreate.pluginObject.createExpression(onValueChange, fieldName, expressionObj, pluginToCreate.config) : null;
  }

};
export default window.PluginManager;