import {get, has, isEmpty} from "lodash"
import React, {PropsWithChildren} from "react"
import {useSelector} from "react-redux"
import nodeAttributes from "../../../assets/json/nodeAttributes.json"
import NkModalStyles from "../../../common/NkModalStyles"
import {getProcessDefinitionData} from "../../../reducers/selectors/settings"
import {NodeType} from "../../../types"
import SvgDiv from "../../SvgDiv"
import {ComponentIcon} from "../../toolbars/creator/ComponentIcon"
import NodeUtils from "../NodeUtils"
import {getComponentSettings} from "./node/selectors"

enum HeaderType {
  SUBTYPE_DOCS,
  SUBTYPE,
  DOCS,
  DEFAULT
}

const nodeClassProperties = [`service.id`, `ref.typ`, `nodeType`, `ref.id`]

const findNodeClass = (node: NodeType) => get(node, nodeClassProperties.find((property) => has(node, property)))

const getNodeAttributes = (node: NodeType) => nodeAttributes[NodeUtils.nodeType(node)]

const getModalHeaderType = (docsUrl?: string, nodeClass?: string) => {
  if (docsUrl && nodeClass) {
    return HeaderType.SUBTYPE_DOCS
  } else if (nodeClass) {
    return HeaderType.SUBTYPE
  } else if (docsUrl) {
    return HeaderType.DOCS
  } else {
    return HeaderType.DEFAULT
  }
}

const Docs = (props: PropsWithChildren<{className: string, docsUrl: string}>) => {
  const {className, children, docsUrl} = props
  return (
    <a className="docsLink" target="_blank" href={docsUrl} title="Documentation" rel="noreferrer">
      <div className={className}>
        {children && <span>{children}</span>}
        <SvgDiv className="docsIcon" svgFile={"documentation.svg"}/>
      </div>
    </a>
  )
}

const Subtype = ({children}: PropsWithChildren<unknown>) => {
  return (
    <div className="modal-subtype-header"><span>{children}</span></div>
  )
}

const NodeClassDocs = ({nodeClass, docsUrl}: {nodeClass?: string, docsUrl?: string}) => {
  switch (getModalHeaderType(docsUrl, nodeClass)) {
    case HeaderType.SUBTYPE_DOCS :
      return <Docs docsUrl={docsUrl} className="modal-subtype-header-docs">{nodeClass}</Docs>
    case HeaderType.SUBTYPE :
      return <Subtype>{nodeClass}</Subtype>
    case HeaderType.DOCS :
      return <Docs docsUrl={docsUrl} className="modal-docs-link">{nodeClass}</Docs>
    default :
      return null
  }
}

const NodeDetailsModalHeader = ({node}: {node: NodeType}): JSX.Element => {
  const processDefinitionData = useSelector(getProcessDefinitionData)
  const {docsUrl} = useSelector(getComponentSettings)

  const attributes = getNodeAttributes(node)
  const titleStyles = NkModalStyles.headerStyles(attributes.styles.fill, attributes.styles.color)
  const variableLanguage = node?.value?.language
  const header = (isEmpty(variableLanguage) ? "" : `${variableLanguage} `) + attributes.name

  const nodeClass = findNodeClass(node)

  return (
    <div className="modalHeader">
      <div className="modal-title-container modal-draggable-handle">
        <div className="modal-title" style={titleStyles}>
          <ComponentIcon node={node} className="modal-title-icon"/>
          <span>{header}</span>
        </div>
      </div>
      <NodeClassDocs nodeClass={nodeClass} docsUrl={docsUrl}/>
    </div>
  )
}

export default NodeDetailsModalHeader
