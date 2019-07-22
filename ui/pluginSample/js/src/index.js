import React from 'react'
import PluginManager from 'PluginManager'

import QueryBuilder from "react-querybuilder";

PluginManager.register('queryBuilder', (name, language) => name.startsWith('id'), (onValueChange, fieldName, expressionObj, config) => {
  //return (<input onChange={event => onValueChange(event.target.value)} value={expressionObj.expression ? expressionObj.expression : "Custom: " + fieldName}/>)
  return (<QueryBuilder fields={config.fields} onQueryChange={query => {
    console.log("log: ", query);
    onValueChange(query);
  }} />);
});

