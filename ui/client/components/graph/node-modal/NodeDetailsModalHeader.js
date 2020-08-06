import _ from "lodash"
import PropTypes from "prop-types"
import React from "react"
import nodeAttributes from "../../../assets/json/nodeAttributes"
import NkModalStyles from "../../../common/NkModalStyles"
import SvgDiv from "../../SvgDiv"
import NodeUtils from "../NodeUtils"
import {getIconHref} from "../EspNode"

const HeaderType = {
  SUBTYPE_DOCS: 1,
  SUBTYPE: 2,
  DOCS: 3,
  DEFAULT: 4,
}

const nodeClassProperties = ["service.id", "ref.typ", "nodeType", "ref.id"]

const findNodeClass = (node) => _.get(node, _.find(nodeClassProperties, (property) => _.has(node, property)))

const getNodeAttributes = (node) => nodeAttributes[NodeUtils.nodeType(node)]

const getModalHeaderType = (docsUrl, nodeClass) => {
  if (docsUrl && nodeClass) {
    return HeaderType.SUBTYPE_DOCS
  } else if (nodeClass) {
    return HeaderType.SUBTYPE
  } else if (docsUrl) {
    return HeaderType.DOCS
  }
  return HeaderType.DEFAULT
}

const Docs = (props) => {
  const {className, nodeClass, docsUrl} = props
  return (
    <a className="docsLink" target="_blank" href={docsUrl} title="Documentation">
      <div className={className}>
        {nodeClass && <span>{nodeClass}</span>}
        <SvgDiv className="docsIcon" svgFile={"documentation.svg"}/>
      </div>
    </a>
  )
}

Docs.propTypes = {
  className: PropTypes.string.isRequired,
  nodeSettings: PropTypes.object,
  nodeClass: PropTypes.string,
}

const Subtype = (props) => {
  const {nodeClass} = props
  return (
    <div className="modal-subtype-header">
      <span>{nodeClass}</span>
    </div>
  )
}

Subtype.propTypes = {
  nodeClass: PropTypes.string.isRequired,
}

const renderNodeClassDocs = (nodeClass, docsUrl) => {
  switch (getModalHeaderType(docsUrl, nodeClass)) {
    case HeaderType.SUBTYPE_DOCS :
      return <Docs nodeClass={nodeClass} docsUrl={docsUrl} className="modal-subtype-header-docs"/>
    case HeaderType.SUBTYPE :
      return <Subtype nodeClass={nodeClass}/>
    case HeaderType.DOCS :
      return <Docs nodeClass={nodeClass} docsUrl={docsUrl} className="modal-docs-link"/>
    default :
      return null
  }
}

const NodeDetailsModalHeader = (props) => {
  const {nodeSettings, node} = props
  const docsUrl = nodeSettings.docsUrl

  const attributes = getNodeAttributes(node)
  const titleStyles = NkModalStyles.headerStyles(attributes.styles.fill, attributes.styles.color)
  const variableLanguage = node?.value?.language
  const header = (_.isEmpty(variableLanguage) ? "" : `${variableLanguage} `) + attributes.name

  const nodeIcon = _.has(node, "type") ? getIconHref(node, nodeSettings) : null
  const nodeClass = findNodeClass(node)

  return (
    <div className="modalHeader">
      <div className="modal-title-container modal-draggable-handle">
        <div className="modal-title" style={titleStyles}>
          {nodeIcon ? <img className="modal-title-icon" src={nodeIcon}/> : null}
          <span>{header}</span>
        </div>
      </div>
      {renderNodeClassDocs(nodeClass, docsUrl)}
    </div>
  )
}

NodeDetailsModalHeader.propTypes = {
  node: PropTypes.object.isRequired,
  docsUrl: PropTypes.string,
}

export default NodeDetailsModalHeader
