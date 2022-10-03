import _, {concat, zip} from "lodash"
import React, {useEffect} from "react"

const parametersEquals = (oldParameter, newParameter) => oldParameter &&
  newParameter &&
  oldParameter.name === newParameter.name

const newFields = (oldParameters, newParameters) => _.differenceWith(newParameters, oldParameters, parametersEquals)
const removedFields = (oldParameters, newParameters) => _.differenceWith(oldParameters, newParameters, parametersEquals)
const unchangedFields = (oldParameters, newParameters) => _.intersectionWith(oldParameters, newParameters, parametersEquals)

const nodeDefinitionParameters = node => node?.ref.parameters

export default function ParameterList({
  createListField,
  createReadOnlyField,
  editedNode,
  processDefinitionData,
  savedNode,
  setNodeState,
}) {
  const nodeDefinitionByName = node => _(processDefinitionData.componentGroups)?.flatMap(c => c.components)?.find(n => n.node.type === node.type && n.label === node.ref.id)?.node
  const nodeId = savedNode.id
  const savedParameters = nodeDefinitionParameters(savedNode)
  const definitionParameters = nodeDefinitionParameters(nodeDefinitionByName(savedNode))
  const diffParams = {
    added: newFields(savedParameters, definitionParameters),
    removed: removedFields(savedParameters, definitionParameters),
    unchanged: unchangedFields(savedParameters, definitionParameters),
  }
  const newParams = concat(diffParams.unchanged, diffParams.added)
  const parametersChanged = !zip(newParams, nodeDefinitionParameters(editedNode)).reduce((acc, params) => acc && parametersEquals(params[0], params[1]), true)
  //If subprocess parameters changed, we update state of parent component and will be rerendered, current node state is probably not ready to be rendered
  //TODO: setting state in parent node is a bit nasty.

  useEffect(() => {
    if (parametersChanged) {
      setNodeState(newParams)
    }
  }, [newParams, parametersChanged, setNodeState])

  if (parametersChanged) {
    return null
  }

  return (
    <span>
      {diffParams.unchanged.map((params, index) => {
        return (
          <div className="node-block" key={nodeId + params.name + index}>
            {createListField(params, index)}
          </div>
        )
      })}
      {diffParams.added.map((params, index) => {
        const newIndex = index + diffParams.unchanged.length
        return (
          <div className="node-block added" key={nodeId + params.name + newIndex}>
            {createListField(params, newIndex)}
          </div>
        )
      })}
      {diffParams.removed.map((params, index) => {
        return (
          <div className="node-block removed" key={nodeId + params.name + index}>
            {createReadOnlyField(params)}
          </div>
        )
      })}
    </span>
  )

}
