/* eslint-disable i18next/no-literal-string */
import _ from "lodash"
import ProcessUtils from "../../../common/ProcessUtils"
import customAttrs from "../../../assets/json/nodeAttributes.json"
import NodeUtils from "../NodeUtils"
import expandIcon from "../../../assets/img/expand.svg"
import * as joint from "jointjs/index"
import {rectWidth, rectHeight, nodeLabelFontSize, summaryCountConfig, edgeStroke, maxLineLength, maxLineCount} from "./misc"
import {getIconHref} from "./getIconHref"
import nodeMarkup from "../markups/node.html"

const attrsConfig = () => {
  return {
    ".": {
      magnet: false,
    },
    ".body": {
      fill: "none",
      width: rectWidth,
      height: rectHeight,
      stroke: "#B5B5B5",
      strokeWidth: 1,
    },
    ".background": {
      width: rectWidth,
      height: rectHeight,
    },
    ".disabled-node-layer": {
      width: rectWidth,
      height: rectHeight,
      zIndex: 0,
    },
    text: {
      fill: "#1E1E1E",
      pointerEvents: "none",
      fontWeight: 400,
    },
    ".nodeIconPlaceholder": {
      x: 0,
      y: 0,
      height: rectHeight,
      width: rectHeight,
    },
    ".nodeIconItself": {
      width: rectHeight / 2,
      height: rectHeight / 2,
      ref: ".nodeIconPlaceholder",
      refX: rectHeight / 4,
      refY: rectHeight / 4,
    },
    ".contentText": {
      fontSize: nodeLabelFontSize,
      ref: ".nodeIconPlaceholder",
      refX: rectHeight + 10,
      refY: rectHeight / 2,
      textVerticalAnchor: "middle",
    },
    ".testResultsPlaceholder": {
      ref: ".nodeIconPlaceholder",
      refX: rectWidth,
      y: 0,
      height: rectHeight,
      width: rectHeight,
    },
    ".testResultsSummary": {
      textAnchor: "middle",
      alignmentBaseline: "middle",
    },
  }
}
const portsAttrs = () => {
  return {
    ".port": {
      refX: 0,
      refY: 0,
    },
    ".port-body": {
      r: 5,
      magnet: true,
      fontSize: 10,
    },
  }
}
const portInAttrs = () => {
  return Object.assign({}, portsAttrs(), {
    ".port circle": {
      fill: "#FFFFFF",
      magnet: "passive",
      stroke: edgeStroke,
      strokeWidth: "1",
      type: "input",
    },
  })
}
const portOutAttrs = () => {
  return Object.assign({}, portsAttrs(), {
    ".port circle": {
      fill: "#FFFFFF",
      stroke: edgeStroke,
      strokeWidth: "1",
      type: "output",
    },
  })
}

joint.shapes.devs.EspNode = joint.shapes.devs.Model.extend({
  markup: nodeMarkup,
  portMarkup: "<g class=\"port\"><circle class=\"port-body\"/></g>",
  portLabelMarkup: null,

  defaults: joint.util.deepSupplement({
    type: "devs.GenericModel",
    attrs: attrsConfig(),
    size: {width: 1, height: 1},
    inPorts: [],
    outPorts: [],
    ports: {
      groups: {
        in: {
          position: "top",
          attrs: portInAttrs(),
        },
        out: {
          position: "bottom",
          attrs: portOutAttrs(),
        },
      },
    },
  }, joint.shapes.devs.Model.prototype.defaults),
})

function getBodyContent(node) {
  const bodyContent = node.id || ""

  if (bodyContent.length <= maxLineLength) {
    return {
      text: bodyContent,
      multiline: false,
    }
  }

  const splitContent = bodyContent.split(" ")

  if (splitContent[0].length > maxLineLength) {
    return {
      text: `${bodyContent.slice(0, maxLineLength)}...`,
      multiline: false,
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

function getTestResultsSummaryAttr(processCounts, width, testResultsWidth) {
  const {breakPoint, maxExtraDigits} = summaryCountConfig

  const hasCounts = !_.isEmpty(processCounts)
  const hasErrors = hasCounts && processCounts && processCounts.errors > 0
  const countsContent = hasCounts ? processCounts ? `${processCounts.all}` : "0" : ""
  let extraDigitsCount = Math.max(countsContent.length - breakPoint, 0)
  extraDigitsCount = Math.min(extraDigitsCount, maxExtraDigits)

  return {
    text: countsContent,
    fill: hasErrors ? "red" : "#CCCCCC",
    refX: width - testResultsWidth / 2,
    // magic/hack: central vertical position when font-size changes
    y: 78 - extraDigitsCount * 1.5,
    height: 16,
  }
}

export function makeElement(node, processCounts, nodesSettings) {
  const description = _.get(node.additionalFields, "description", null)
  const {text: bodyContent, multiline} = getBodyContent(node)
  const hasCounts = !_.isEmpty(processCounts)
  const width = rectWidth
  const height = rectHeight
  const nodeSettings = nodesSettings?.[ProcessUtils.findNodeConfigName(node)]
  const iconHref = getIconHref(node, nodeSettings)
  const testResultsHeight = 24
  const pxPerChar = 8
  const countsPadding = 8
  //dynamically sized width
  const testResultsWidth = _.toArray(_.toString(processCounts ? processCounts.all : "")).length * pxPerChar + 2 * countsPadding
  const attrs = {
    ".background": {
      width: width,
      opacity: node.isDisabled ? 0.4 : 1,
    },
    ".disabled-node-layer": {
      display: node.isDisabled ? "block" : "none",
      width: width,
      fill: "#B3B3B3",
    },
    ".background title": {
      text: description,
    },
    ".body": {
      width: width,
    },
    "rect.nodeIconPlaceholder": {
      fill: customAttrs[node.type].styles.fill,
      opacity: node.isDisabled ? 0.4 : 1,
    },
    ".nodeIconItself": {
      "xlink:href": iconHref,
    },
    ".contentText": {
      text: bodyContent,
      opacity: node.isDisabled ? 0.65 : 1,
    },
    ".testResultsPlaceHolder": {
      noExport: "",
      display: hasCounts ? "block" : "none",
      width: testResultsWidth,
      refX: width - testResultsWidth,
      refY: height,
      height: testResultsHeight,
    },
    ".testResultsSummary": {
      noExport: "",
      ...getTestResultsSummaryAttr(processCounts, width, testResultsWidth),
    },
    ".groupElements": {
      display: NodeUtils.nodeIsGroup(node) ? "block" : "none",
    },
    ".expandIcon": {
      "xlink:href": expandIcon,
      width: 26,
      height: 26,
      refX: width - 13,
      refY: -13,
    },
  }

  const inPorts = NodeUtils.hasInputs(node) ? ["In"] : []
  const outPorts = NodeUtils.hasOutputs(node) ? ["Out"] : []

  return new joint.shapes.devs.EspNode({
    id: node.id,
    size: {width: width, height: height},
    inPorts: inPorts,
    outPorts: outPorts,
    attrs: attrs,
    rankDir: "R",
    nodeData: node,
    //This is used by jointjs to handle callbacks/changes
    //TODO: figure out what should be here?
    definitionToCompare: {
      node: _.cloneDeepWith(node, (val, key: string) => ["branchParameters", "parameters"].indexOf(key) > -1 ? null : undefined),
      processCounts: processCounts,
    },
  })
}

