import {memoize} from "lodash"
import ProcessUtils from "../../../common/ProcessUtils"
import {absoluteBePath} from "../../../common/UrlUtils"
import {NodeType, ProcessDefinitionData} from "../../../types"

export const preloadImage = memoize((href: string) => new Promise<string>(resolve => {
  const image = new Image()
  image.src = href
  return image.onload = () => resolve(href)
}))

export const getNodeIconSrc = memoize((node: NodeType, processDefinitionData: ProcessDefinitionData) => {
  if (node) {
    const nodeSettings = processDefinitionData.nodesConfig?.[ProcessUtils.findNodeConfigName(node)]
    const iconFromConfig = nodeSettings?.icon
    const defaultIconName = `${node.type}.svg`
    const src = absoluteBePath(`/assets/nodes/${iconFromConfig || defaultIconName}`)
    preloadImage(src)
    return src
  }
  return null
})
