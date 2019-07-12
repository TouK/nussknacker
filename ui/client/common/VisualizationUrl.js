import _ from "lodash";
import Moment from "moment";
import * as  queryString from 'query-string'

export const visualizationRouterBasePath = "/visualization"
export const visualizationRouterPath = visualizationRouterBasePath + '/:processId'

export function visualizationUrl(path, processName, nodeId, edgeId) {
  if (!_.isEmpty(nodeId) && !_.isEmpty(edgeId)) {
    throw new Error("cannot visualize both nodeId and edgeId")
  }
  const baseUrl = `${path}/${encodeURIComponent(processName)}`
  const nodeIdPart = nodeId ? this.nodeIdPart(nodeId) : ""
  const edgeIdPart = edgeId ? this.edgeIdPart(edgeId) : ""
  return baseUrl + nodeIdPart + edgeIdPart
}

export function nodeIdPart(nodeId) {
  return `?nodeId=${encodeURIComponent(nodeId)}`
}

export function edgeIdPart(edgeId) {
  return `?edgeId=${encodeURIComponent(edgeId)}`
}

export function extractVisualizationParams(search) {
  let queryParams = queryString.parse(search)
  const nodeId = queryParams.nodeId;
  const edgeId = queryParams.edgeId;
  return {nodeId, edgeId}
}

export function extractBusinessViewParams(queryParams) {
  if (queryParams.businessView) {
    return queryParams.businessView.toLowerCase() === "true"
  }

  return false
}

export function extractCountParams(queryParams) {
  if (queryParams.from || queryParams.to) {
    const from = queryParams.from ? fromTimestampOrDate(queryParams.from) : null;
    const to = queryParams.to ? fromTimestampOrDate(queryParams.to) : Moment();
    return {from, to};
  }

  return null
}

function fromTimestampOrDate(tsOrDate) {
  const asInt = parseInt(tsOrDate);
  if (Number.isInteger(asInt) && !isNaN(tsOrDate))
    return Moment(asInt);
  else
    return Moment(tsOrDate);
}

export function setAndPreserveLocationParams(params){
  let queryParams = queryString.parse(window.location.search, {arrayFormat: 'comma'})
  let resultParams = _.omitBy(Object.assign({}, queryParams, params), (e) => {
    return e == null || e === "" || e === 0
  })

  return queryString.stringify(resultParams, {arrayFormat: 'comma'})
}