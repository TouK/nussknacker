/* eslint-disable i18next/no-literal-string */
import * as joint from "jointjs"
import {dia} from "jointjs"
import {cloneDeepWith, get, isEmpty, toString} from "lodash"
import customAttrs from "../../../assets/json/nodeAttributes.json"
import ProcessUtils from "../../../common/ProcessUtils"
import {ProcessCounts} from "../../../reducers/graph"
import {NodeType, ProcessDefinitionData} from "../../../types"
import {setLinksHovered} from "../dragHelpers"
import {isModelElement} from "../GraphPartialsInTS"
import {Events} from "../joint-events"
import NodeUtils from "../NodeUtils"
import {EspGroupShape, EspNodeShape} from "./esp"
import {getIconHref} from "./getIconHref"

const maxLineLength = 24
const maxLineCount = 2

function getBodyContent(bodyContent = ""): {text: string, multiline?: boolean} {
  if (bodyContent.length <= maxLineLength) {
    return {
      text: bodyContent,
    }
  }

  const splitContent = bodyContent.split(" ")

  if (splitContent[0].length > maxLineLength) {
    return {
      text: `${bodyContent.slice(0, maxLineLength)}...`,
    }
  }

  const tmpLines = [splitContent[0]]

  for (const str of splitContent.slice(1)) {
    const idx = tmpLines.length - 1

    if (tmpLines[idx].length + str.length <= maxLineLength) {
      tmpLines[idx] += ` ${str}`
      continue
    }

    if (tmpLines.length >= maxLineCount) {
      tmpLines[idx] += "..."
      break
    }

    if (str.length > maxLineLength) {
      tmpLines[idx + 1] = `${str.slice(0, maxLineLength)}...`
      break
    }

    tmpLines[idx + 1] = str
  }

  const idx = tmpLines.length - 1
  if (tmpLines[idx].length > maxLineLength) {
    tmpLines[idx] = `${tmpLines[idx].slice(0, maxLineLength)}...`
  }

  return {
    text: tmpLines.join("\n"),
    multiline: tmpLines.length > 1,
  }
}

function getStringWidth(str = "", pxPerChar = 0, padding = 0) {
  return toString(str).length * pxPerChar + 2 * padding
}

export const makeElement = (counts: ProcessCounts, processDefinitionData: ProcessDefinitionData) => {
  const nodesSettings = processDefinitionData.nodesConfig || {}
  return (node: NodeType) => {
    const description = get(node.additionalFields, "description", null)
    const {text: bodyContent} = getBodyContent(node.id)

    const nodeSettings = nodesSettings?.[ProcessUtils.findNodeConfigName(node)]
    const iconHref = getIconHref(node, nodeSettings)

    const processCounts = counts[node.id]
    const hasCounts = !isEmpty(processCounts)
    const hasErrors = hasCounts && processCounts?.errors > 0
    const testCounts = hasCounts ? processCounts?.all || 0 : ""
    const testResultsWidth = getStringWidth(testCounts, 8, 8)

    const attributes: joint.shapes.devs.ModelAttributes = {
      id: node.id,
      inPorts: NodeUtils.hasInputs(node) ? ["In"] : [],
      outPorts: NodeUtils.hasOutputs(node) ? ["Out"] : [],
      attrs: {
        background: {
          opacity: node.isDisabled ? 0.4 : 1,
        },
        title: {
          text: description,
        },
        iconBackground: {
          fill: customAttrs[node.type].styles.fill,
          opacity: node.isDisabled ? 0.4 : 1,
        },
        icon: {
          xlinkHref: iconHref,
        },
        content: {
          text: bodyContent,
          opacity: node.isDisabled ? 0.65 : 1,
        },
        testResultsSummary: {
          text: testCounts,
          fill: hasErrors ? "red" : "#CCCCCC",
          x: -testResultsWidth / 2,
        },
        testResults: {
          display: hasCounts ? "block" : "none",
          width: testResultsWidth,
          x: -testResultsWidth,
        },
      },
      rankDir: "R",
      nodeData: node,
      //This is used by jointjs to handle callbacks/changes
      //TODO: figure out what should be here?
      definitionToCompare: {
        node: cloneDeepWith(node, (val, key: string) => [
          "additionalFields",
          "branchParameters",
          "parameters",
        ].includes(key) ?
          null :
          undefined),
        processCounts,
      },
    }

    const element = NodeUtils.nodeIsGroup(node) ? new EspGroupShape(attributes) : new EspNodeShape(attributes)

    element.once(Events.ADD, (e: dia.Element) => {
      // add event listeners after element setup
      setTimeout(() => {
        e.on(Events.CHANGE_POSITION, (el: dia.Element) => {
          if (isModelElement(el) && !el.graph.getNeighbors(el).length) {
            setLinksHovered(el.graph, el.getBBox())
          }
        })
      })
    })

    return element
  }
}
