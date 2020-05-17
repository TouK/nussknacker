import React, {useMemo} from "react"
import {useDrag} from "react-dnd"
import "../stylesheets/toolBox.styl"
import {cloneDeep, memoize} from "lodash"
import {NodeType, PossibleNode} from "../types"
import {useSelector} from "react-redux"
import {getProcessDefinitionData} from "../reducers/selectors/settings"
import ProcessUtils from "../common/ProcessUtils"
import {absoluteBePath} from "../common/UrlUtils"

export const DndTypes = {
  ELEMENT: "element",
}

type OwnProps = {
  nodeModel: NodeType,
  label: string,
  icon: string,
}

export default function Tool(props: OwnProps) {
  const {label, nodeModel, icon} = props
  const [collectedProps, drag] = useDrag({
    item: {...cloneDeep(nodeModel), id: label, type: DndTypes.ELEMENT},
    begin: () => ({...cloneDeep(nodeModel), id: label}),
  })

  return (
    <div className="tool" ref={drag}>
      <div className="toolWrapper">
        <img src={icon} alt={"node icon"} className="toolIcon"/>
        {label}
      </div>
    </div>
  )
}

const preloadImage = memoize((href: string) => new Image().src = href)

export function useToolIcon(node: PossibleNode) {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const iconSrc = useMemo(
    () => {
      const nodesSettings = processDefinitionData.nodesConfig || {}
      const iconFromConfig = (nodesSettings[ProcessUtils.findNodeConfigName(node.node)] || {}).icon
      const defaultIconName = `${node.node.type}.svg`
      return absoluteBePath(`/assets/nodes/${iconFromConfig ? iconFromConfig : defaultIconName}`)
    },
    [node, processDefinitionData],
  )
  preloadImage(iconSrc)
  return iconSrc
}
