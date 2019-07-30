import React from 'react'
import PluginManager from 'PluginManager'

const queryPlugin = {
  canCreateExpression(fieldName, language) {
    return language === 'literal'
  },

  //we use default value from configuration
  createExpression(onValueChange, fieldName, expressionObj, config) {
    return (<input type="text" className="node-input" value={expressionObj.expression ? expressionObj.expression : config.defaultValue }
                   onChange={e => onValueChange(e.target.value)}/> )
  }
}

PluginManager.register('literalExpressions', queryPlugin);

