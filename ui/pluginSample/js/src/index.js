import React from 'react'
import PluginManager from 'PluginManager'

const queryPlugin = {
  canCreateExpression(fieldName, language) {
    return language === 'literal'
  },

  createExpression(onValueChange, fieldName, expressionObj, config) {
    return (<input type="text" value={expressionObj.expression ? config.defaultExpression : expressionObj.expression}
                   onChange={e => onValueChange(e.target.value)}/> )
  }
}

PluginManager.register('queryBuilder', queryPlugin);

