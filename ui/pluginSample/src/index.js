import React from 'react'

console.log("TESSTING??");

window.customField123 = (onValueChange, fieldName, expressionObj) => {
  return (
    <input onChange={onValueChange} value={expressionObj.expression ? expressionObj.expression : "Custom: " + fieldName}/>
  );
}

