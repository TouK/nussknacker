/* eslint-disable i18next/no-literal-string */
import _ from "lodash"
import expandIcon from "../../../assets/img/expand.svg"
import customAttrs from "../../../assets/json/nodeAttributes.json"
import * as LoaderUtils from "../../../common/LoaderUtils"
import ProcessUtils from "../../../common/ProcessUtils"
import SVGUtils from "../../../common/SVGUtils"
import {absoluteBePath} from "../../../common/UrlUtils"
import NodeUtils from "../NodeUtils"
import {EspNodeShape} from "./esp"
import {maxLineCount, maxLineLength, rectHeight, rectWidth, summaryCountConfig} from "./misc"

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

export function makeElement(node, processCounts, forExport, nodesSettings) {
  const description = _.get(node.additionalFields, "description", null)
  const {text: bodyContent} = getBodyContent(node)
  const hasCounts = !_.isEmpty(processCounts)
  const width = rectWidth
  const height = rectHeight
  const iconFromConfig = (nodesSettings[ProcessUtils.findNodeConfigName(node)] || {}).icon
  const defaultIconName = `${node.type}.svg`
  const iconHref = forExport ?
    // TODO: Currently we encode icon data to have standalone svg that can be used to generate pdf
    //       it will works only with assets available on FE side. We should switch to fetching all icons from BE
    //       but it will cause async fetching on some step
    SVGUtils.svgToDataURL(LoaderUtils.loadNodeSvgContent(defaultIconName)) :
    absoluteBePath(`/assets/nodes/${iconFromConfig ? iconFromConfig : defaultIconName}`)
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
      display: hasCounts && !forExport ? "block" : "none",
      width: testResultsWidth,
      refX: width - testResultsWidth,
      refY: height,
      height: testResultsHeight,
    },
    ".testResultsSummary": getTestResultsSummaryAttr(processCounts, width, testResultsWidth),
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

  return new EspNodeShape({
    id: node.id,
    size: {width: width, height: height},
    inPorts: inPorts,
    outPorts: outPorts,
    attrs: attrs,
    rankDir: "R",
    nodeData: node,
    definitionToCompare: {
      node: node,
      processCounts: processCounts,
      forExport: forExport,
    },
  })
}
