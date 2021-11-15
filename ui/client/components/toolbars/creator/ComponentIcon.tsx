import {isString, memoize} from "lodash"
import React from "react"
import {useSelector} from "react-redux"
import ProcessUtils from "../../../common/ProcessUtils"
import {absoluteBePath} from "../../../common/UrlUtils"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {NodeType, ProcessDefinitionData} from "../../../types"
import SvgDiv from "../../SvgDiv"

export const preloadImage = memoize((href: string) => new Promise<string>(resolve => {
  const image = new Image()
  image.src = href
  return image.onload = () => resolve(href)
}))

function _getComponentIconSrc(nodeOrPath: string): string
function _getComponentIconSrc(nodeOrPath: NodeType, processDefinitionData: ProcessDefinitionData): string | null
function _getComponentIconSrc(nodeOrPath: NodeType | string, processDefinitionData?: ProcessDefinitionData): string | null {
  if (nodeOrPath) {
    if (isString(nodeOrPath)) {
      const src = absoluteBePath(nodeOrPath)
      preloadImage(src)
      return src
    }

    const nodeComponentId = ProcessUtils.findNodeConfigName(nodeOrPath)
    const componentConfig = processDefinitionData?.componentsConfig?.[nodeComponentId]
    const iconFromConfig = componentConfig?.icon
    const iconBasedOnType = nodeOrPath.type && `/assets/components/${nodeOrPath.type}.svg`
    const icon = iconFromConfig || iconBasedOnType

    if (icon) {
      const src = absoluteBePath(icon)
      preloadImage(src)
      return src
    }
  }

  return null
}

export const getComponentIconSrc = memoize(_getComponentIconSrc)

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
