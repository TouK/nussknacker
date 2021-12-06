/* eslint-disable i18next/no-literal-string */
import {attributes, dia, shapes} from "jointjs"
import {cloneDeepWith, get, isEmpty, toString} from "lodash"
import customAttrs from "../../../assets/json/nodeAttributes.json"
import {ProcessCounts} from "../../../reducers/graph"
import {NodeType, ProcessDefinitionData} from "../../../types"
import {getComponentIconSrc} from "../../toolbars/creator/ComponentIcon"
import {setLinksHovered} from "../dragHelpers"
import {isConnected, isModelElement} from "../GraphPartialsInTS"
import {Events} from "../joint-events"
import NodeUtils from "../NodeUtils"
import {EspNodeShape} from "./esp"
import {startsWithEmoji} from "../../../common/startsWithEmoji"

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

export function getStringWidth(str = "", pxPerChar = 8, padding = 7): number {
  return toString(str).length * pxPerChar + 2 * padding
}

export const updateNodeCounts = (processCounts :ProcessCounts) => (node: shapes.devs.Model): void => {
  const count = processCounts[node.id]
  const hasCounts = !isEmpty(count)
  const hasErrors = hasCounts && count?.errors > 0
  const testCounts = hasCounts ? count?.all.toString() || "0" : ""
  const testResultsWidth = getStringWidth(testCounts)

  const testResultsSummary: attributes.SVGTextAttributes = {
    text: testCounts,
    fill: "#cccccc",
    x: testResultsWidth / 2,
  }
  const testResults: attributes.SVGRectAttributes = {
    display: hasCounts ? "block" : "none",
    fill: hasErrors ? "#662222": "#4d4d4d",
    stroke: hasErrors ? "red": "#4d4d4d",
    strokeWidth: hasErrors ? 3 : 0,
    width: testResultsWidth,
  }
  node.attr({testResultsSummary, testResults})
}

export function makeElement(processDefinitionData: ProcessDefinitionData): (node: NodeType) => shapes.devs.Model {
  return (node: NodeType) => {
    const description = get(node.additionalFields, "description", null)
    const {text: bodyContent} = getBodyContent(node.id)

    const iconHref = getComponentIconSrc(node, processDefinitionData)

    const attributes: shapes.devs.ModelAttributes = {
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
          fontSize: startsWithEmoji(bodyContent)?50:undefined,
          refY: startsWithEmoji(bodyContent)?5:undefined,
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
      },
    }

    const element = new EspNodeShape(attributes)

    element.once(Events.ADD, (e: dia.Element) => {
      // add event listeners after element setup
      setTimeout(() => {
        e.on(Events.CHANGE_POSITION, (el: dia.Element) => {
          if (isModelElement(el) && !isConnected(el) && el.hasPort("In") && el.hasPort("Out")) {
            setLinksHovered(el.graph, el.getBBox())
          }
        })
      })
    })

    return element
  }
}
