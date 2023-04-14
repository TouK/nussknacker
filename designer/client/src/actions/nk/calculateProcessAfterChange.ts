import NodeUtils from "../../components/graph/NodeUtils"
import {fetchProcessDefinition} from "./processDefinitionData"
import {getProcessDefinitionData} from "../../reducers/selectors/settings"
import {mapProcessWithNewNode, replaceNodeOutputEdges} from "../../components/graph/GraphUtils"
import {alignSubprocessWithSchema} from "../../components/graph/SubprocessSchemaAligner"
import {Edge, NodeType, Process} from "../../types"
import {ThunkAction} from "../reduxTypes"

function alignSubprocessesNodeWithSchema(process, processDefinitionData) {
  return {
    ...process,
    nodes: process.nodes.map((node) => {
      return node.type === "SubprocessInput" ?
        alignSubprocessWithSchema(processDefinitionData, node) :
        node
    }),
  }
}

export function calculateProcessAfterChange(process: Process, before: NodeType, after: NodeType, outputEdges: Edge[]): ThunkAction<Promise<Process>> {
  return async (dispatch, getState) => {

    if (NodeUtils.nodeIsProperties(after)) {
      const processDef = await dispatch(fetchProcessDefinition(process.processingType, process.properties.isSubprocess))
      const processWithNewSubprocessSchema = alignSubprocessesNodeWithSchema(process, processDef.processDefinitionData)
      const {id, ...properties} = after
      if (id?.length && id !== before.id) {
        dispatch({type: "PROCESS_RENAME", name: id})
      }
      return {...processWithNewSubprocessSchema, properties}
    }

    let changedProcess = process
    if (outputEdges) {
      const processDefinitionData = getProcessDefinitionData(getState())
      const filtered = outputEdges.map(({to, ...e}) => changedProcess.nodes.find(n => n.id === to) ? {...e, to} : {
        ...e,
        to: ""
      })
      changedProcess = replaceNodeOutputEdges(process, processDefinitionData, filtered, before.id)
    }

    return mapProcessWithNewNode(changedProcess, before, after)
  }
}
