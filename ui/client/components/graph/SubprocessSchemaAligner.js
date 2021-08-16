import fp from "lodash/fp"

export function alignSubprocessWithSchema(processDefinitionData, subprocessNode) {
  const subprocessId = subprocessNode.ref.id
  const subprocessSchema = processDefinitionData.nodesToAdd
    .find((nodesToAdd) => {return nodesToAdd.name === "fragments"}).possibleNodes
    .find((obj) => obj.node.ref.id === subprocessId)
  const subprocessSchemaParameters = subprocessSchema.node.ref.parameters
  const mergedParameters = subprocessSchemaParameters
    .map((param) => subprocessNode.ref.parameters.find((nodeParam) => nodeParam.name === param.name) || param)
  return fp.set("ref.parameters", mergedParameters, subprocessNode)
}
