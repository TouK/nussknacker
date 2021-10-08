import {cloneDeep, memoize} from "lodash"
import React, {useEffect, useMemo} from "react"
import {useDrag} from "react-dnd"
import Highlighter from "react-highlight-words"
import {useSelector} from "react-redux"
import ProcessUtils from "../../../common/ProcessUtils"
import {absoluteBePath} from "../../../common/UrlUtils"
import {useNkTheme} from "../../../containers/theme"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import "../../../stylesheets/toolBox.styl"
import {NodeType} from "../../../types"
import {getEmptyImage} from "react-dnd-html5-backend"

export const DndTypes = {
  ELEMENT: "element",
}

type OwnProps = {
  nodeModel: NodeType,
  label: string,
  highlight?: string,
}

export default function Tool(props: OwnProps): JSX.Element {
  const {label, nodeModel, highlight} = props
  const icon = useToolIcon(nodeModel)
  const [, drag, preview] = useDrag(() => ({
    type: DndTypes.ELEMENT,
    item: {...cloneDeep(nodeModel), id: label},
    options: {dropEffect: "copy"},
  }))

  useEffect(() => {
    preview(getEmptyImage())
    return () => {
      preview(null)
    }
  }, [preview])

  const {theme} = useNkTheme()

  return (
    <div className="tool" ref={drag} data-testid={`component:${label}`}>
      <div className="toolWrapper">
        <img src={icon} alt={`node icon`} className="toolIcon"/>
        <Highlighter
          textToHighlight={label}
          searchWords={[highlight]}
          highlightTag={`span`}
          highlightStyle={{
            color: theme.colors.warning,
            background: theme.colors.secondaryBackground,
          }}
        />
      </div>
    </div>
  )
}

const preloadImage = memoize((href: string) => new Image().src = href)

export function useToolIcon(node: NodeType) {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const iconSrc = useMemo(
    () => {
      if (!node) {
        return null
      }

      const componentsSettings = processDefinitionData.componentsConfig || {}
      const iconFromConfig = (componentsSettings[ProcessUtils.findNodeConfigName(node)] || {}).icon
      const defaultIconName = `${node.type}.svg`
      return absoluteBePath(`/assets/nodes/${iconFromConfig ? iconFromConfig : defaultIconName}`)
    },
    [node, processDefinitionData],
  )

  useEffect(() => {
    if (!iconSrc) {
      return null
    }
    preloadImage(iconSrc)
  }, [iconSrc])

  return iconSrc
}
