import {isString, memoize} from "lodash"
import React from "react"
import {useSelector} from "react-redux"
import ProcessUtils from "../../../common/ProcessUtils"
import {absoluteBePath} from "../../../common/UrlUtils"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {NodeType, ProcessDefinitionData} from "../../../types"
import SvgDiv from "../../SvgDiv"

const preloadImage = memoize((href: string) => new Promise<string>(resolve => {
  const image = new Image()
  image.src = href
  return image.onload = () => resolve(href)
}))

function preloadBeImage(icon: string): string | null {
  if (!icon) {
    return null
  }
  const src = absoluteBePath(icon)
  preloadImage(src)
  return src
}

function getIconFromDef(nodeOrPath: NodeType, processDefinitionData: ProcessDefinitionData): string | null {
  const nodeComponentId = ProcessUtils.findNodeConfigName(nodeOrPath)
  const componentConfig = processDefinitionData?.componentsConfig?.[nodeComponentId]
  const iconFromConfig = componentConfig?.icon
  const iconBasedOnType = nodeOrPath.type && `/assets/components/${nodeOrPath.type}.svg`
  return iconFromConfig || iconBasedOnType || null
}

export const getComponentIconSrc: {
  (path: string): string,
  (node: NodeType, processDefinitionData: ProcessDefinitionData): string | null,
} = memoize((nodeOrPath, processDefinitionData?) => {
  if (nodeOrPath) {
    const icon = isString(nodeOrPath) ? nodeOrPath : getIconFromDef(nodeOrPath, processDefinitionData)
    return preloadBeImage(icon)
  }
  return null
})

export function useComponentIcon(node: NodeType): string {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  return getComponentIconSrc(node, processDefinitionData)
}

export function ComponentIcon({node, className}: { node: NodeType, className?: string }): JSX.Element {
  const icon = useComponentIcon(node)
  if (!icon) {
    return <SvgDiv className={className} svgFile={"properties.svg"}/>
  }
  return <img src={icon} alt={node.type} className={className}/>
}
