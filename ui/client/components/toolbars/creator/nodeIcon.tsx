import {memoize} from "lodash"
import React from "react"
import {useSelector} from "react-redux"
import ProcessUtils from "../../../common/ProcessUtils"
import {absoluteBePath} from "../../../common/UrlUtils"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {NodeType, ProcessDefinitionData} from "../../../types"

export const preloadImage = memoize((href: string) => new Promise<string>(resolve => {
  const image = new Image()
  image.src = href
  return image.onload = () => resolve(href)
}))

export const getNodeIconSrc = memoize((node: NodeType, processDefinitionData: ProcessDefinitionData) => {
  if (node) {
    const nodeSettings = processDefinitionData.componentsConfig?.[ProcessUtils.findNodeConfigName(node)]
    const iconFromConfig = nodeSettings?.icon
    const defaultIconName = `${node.type}.svg`
    const src = absoluteBePath(`/assets/nodes/${iconFromConfig || defaultIconName}`)
    preloadImage(src)
    return src
  }
  return null
})

export function useNodeIcon(node: NodeType): string {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  return getNodeIconSrc(node, processDefinitionData)
}

export function NodeIcon({node, className}: {node: NodeType, className?: string}): JSX.Element {
  const icon = useNodeIcon(node)
  if (!icon) {
    return null
  }
  return <img src={icon} alt={node.type} className={className}/>
}
