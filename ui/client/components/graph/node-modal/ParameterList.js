import _ from "lodash"
import React from "react"

const parametersEquals = (oldParameter, newParameter) =>
  oldParameter
  && newParameter
  && oldParameter.name === newParameter.name

const newFields = (oldParameters, newParameters) => _.differenceWith(newParameters, oldParameters, parametersEquals)
const removedFields = (oldParameters, newParameters) => _.differenceWith(oldParameters, newParameters, parametersEquals)
const unchangedFields = (oldParameters, newParameters) => _.intersectionWith(oldParameters, newParameters, parametersEquals)

const nodeDefinitionParameters = node => node.ref.parameters

export default function ParameterList(props) {
  function nodeDefinitionByName(node) {
    return _(props.processDefinitionData.nodesToAdd)
      .flatMap(c => c.possibleNodes)
      .find(n => n.node.type === node.type && n.label === node.ref.id)
      .node
  }

  const savedParameters = nodeDefinitionParameters(props.savedNode)
  const definitionParameters = nodeDefinitionParameters(nodeDefinitionByName(props.savedNode))
  const diffParams = {
    added: newFields(savedParameters, definitionParameters),
    removed: removedFields(savedParameters, definitionParameters),
    unchanged: unchangedFields(savedParameters, definitionParameters),
  }
  const newParams = _.concat(diffParams.unchanged, diffParams.added)
  if (!_.zip(newParams, nodeDefinitionParameters(props.editedNode)).reduce((acc, params) => acc && parametersEquals(params[0], params[1]), true)) {
    props.setNodeState(newParams)
  }
  const nodeId = props.savedNode.id
  return (
    <span>
        {diffParams.unchanged.map((params, index) => {
          return (
            <div className="node-block" key={nodeId + params.name + index}>
              {props.createListField(params, index)}
            </div>
          )
        })}
      {diffParams.added.map((params, index) => {
        const newIndex = index + diffParams.unchanged.length
        return (
          <div className="node-block added" key={nodeId + params.name + newIndex}>
            {props.createListField(params, newIndex)}
          </div>
        )
      })}
      {diffParams.removed.map((params, index) => {
        return (
          <div className="node-block removed" key={nodeId + params.name + index}>
            {props.createReadOnlyField(params)}
          </div>
        )
      })}
          </span>
  )
}
