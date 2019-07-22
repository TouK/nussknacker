import _ from "lodash";
import {API_URL} from "../config";
import React from "react";
import ReactDOM from 'react-dom';

window.PluginManager = {

  plugins: [],

  configs: {},

  init(plugins) {
    //TODO: figure out how to do it via webpack...
    window.React = React;
    window.ReactDOM = ReactDOM;

    plugins.forEach(plugin => {
      plugin.frontendResourceNames.forEach(pluginFile => {
        const script = document.createElement("script");
        script.src = `${API_URL}/plugins/${plugin.name}/resources/${pluginFile}`;
        script.async = true;
        document.body.appendChild(script);
      });
    });
    this.configs = _.mapValues(_.keyBy(plugins, 'name'), 'frontendConfig')
    console.log("Init with", this.configs);
  },

  //this is invoked by plugin itself in script
  register(name, matches, create) {
    console.log("Initializing plugin", name, this);
    this.plugins.push({
      name: name,
      matches: matches,
      create: create,
      config: this.configs[name]
    });
    console.log("Plugins: ", this.plugins.map(n => n.name))
  }

};
export default window.PluginManager;