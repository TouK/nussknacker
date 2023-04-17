import {isString, memoize} from "lodash"
import React from "react"
import {useSelector} from "react-redux"
import ProcessUtils from "../../../common/ProcessUtils"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {NodeType, ProcessDefinitionData} from "../../../types"
import {ReactComponent as PropertiesSvg} from "../../../assets/img/properties.svg"
import ReactDOM from "react-dom"
import {InlineSvg} from "../../SvgDiv"

const preloadBeImage = memoize((src: string): string | null => {
  if (!src) {
    return null
  }

  const id = `svg${Date.now()}`
  const div = document.createElement("div")
  ReactDOM.render(<InlineSvg src={src} id={id} style={{display: "none"}}/>, div)
  document.body.appendChild(div)
  return `#${id}`
})

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
} = (nodeOrPath, processDefinitionData?) => {
  if (nodeOrPath) {
    const icon = isString(nodeOrPath) ? nodeOrPath : getIconFromDef(nodeOrPath, processDefinitionData)
    return preloadBeImage(icon)
  }
  return null
}

interface Created extends ComponentIconParops {
  processDefinition: ProcessDefinitionData,
}

class Icon extends React.Component<Created> {
  private icon: string

  constructor(props) {
    super(props)
    this.icon = getComponentIconSrc(props.node, props.processDefinition)
  }

  componentDidUpdate() {
    this.icon = getComponentIconSrc(this.props.node, this.props.processDefinition)
  }

  render(): JSX.Element {
    const {icon, props: {className}} = this

    if (!icon) {
      return <PropertiesSvg className={className}/>
    }

    return (
      <svg className={className}>
        <use href={icon}/>
      </svg>
    )
  }
}

interface ComponentIconParops {
  node: NodeType,
  className?: string,
}

export const ComponentIcon = (props: ComponentIconParops) => {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  return <Icon {...props} processDefinition={processDefinitionData}/>
}
