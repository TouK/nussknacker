import _ from "lodash";
import Moment from "moment";
import queryString from 'query-string'

export const visualizationRouterPath = '/visualization/:processId'

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

export function extractVisualizationParams(queryParams) {
  const query = queryString.parse(queryParams)
  const {nodeId, edgeId} = query
  return {nodeId, edgeId}
}

export function extractBusinessViewParams(queryParams) {
  const query = queryString.parse(queryParams);
  if (query.businessView) {
    return query.businessView.toLowerCase() === "true"
  }

  return false
}

export function extractCountParams(queryParams) {
  const query = queryString.parse(queryParams)
  if (query.from || query.to) {
    const from = query.from ? fromTimestampOrDate(query.from) : null;
    const to = query.to ? fromTimestampOrDate(query.to) : Moment();
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
  let queryParams = queryString.parse(location.search)
  let resultParams = _.omitBy(Object.assign({}, queryParams, params), (e) => {
    return e === false || e == null || e === ""
  })

  return queryString.stringify(resultParams)
}