import React from 'react'
import PluginManager from 'PluginManager'

import QueryBuilder from "react-querybuilder";
import 'react-querybuilder/dist/query-builder.scss';


PluginManager.register('queryBuilder', (name, language) => name.startsWith('id'), (onValueChange, fieldName, expressionObj, config) => {
  return (<QueryBuilder
    controlClassnames={{ fields: 'form-control' }}
    fields={config.fields} onQueryChange={query => {
    console.log("log: ", query);
    onValueChange(query);
  }} />);
});

