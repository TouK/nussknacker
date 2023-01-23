import {css, cx} from "@emotion/css"
import {isEmpty} from "lodash"
import React, {MouseEventHandler, SyntheticEvent} from "react"
import {useSelector} from "react-redux"
import {Link} from "react-router-dom"
import NodeUtils from "../../components/graph/NodeUtils"
import {getProcessUnsavedNewName} from "../../reducers/selectors/graph"
import {NodeId, NodeType, Process} from "../../types"
import {useNkTheme} from "../../containers/theme"
import Color from "color"

interface NodeErrorsLinkSectionProps {
  nodeIds: NodeId[],
  message: string,
  showDetails: (event: SyntheticEvent, details: NodeType) => void,
  currentProcess: Process,
  className?: string,
}

export default function NodeErrorsLinkSection(props: NodeErrorsLinkSectionProps): JSX.Element {
  const {nodeIds, message, showDetails, currentProcess, className} = props
  const name = useSelector(getProcessUnsavedNewName)
  const separator = ", "

  return !isEmpty(nodeIds) && (
    <div className={className}>
      <ErrorHeader message={message}/>
      {
        nodeIds.map((nodeId, index) => {
          const details = nodeId === "properties" ?
            NodeUtils.getProcessProperties(currentProcess, name) :
            NodeUtils.getNodeById(nodeId, currentProcess)
          return (
            <React.Fragment key={nodeId}>
              <NodeErrorLink
                onClick={event => showDetails(event, details)}
                nodeId={nodeId}
                disabled={!details}
              />
              {index < nodeIds.length - 1 ? separator : null}
            </React.Fragment>
          )
        })
      }
    </div>
  )
}

const ErrorHeader = (props) => {
  const {message, className} = props

  return <span className={className}>{message}</span>
}

const NodeErrorLink = (props: { onClick: MouseEventHandler<HTMLAnchorElement>, nodeId: NodeId, disabled?: boolean }) => {
  const {onClick, nodeId, disabled} = props
  const {theme} = useNkTheme()

  const styles = css({
    whiteSpace: "normal",
    fontWeight: 600,
    color: theme.colors.error,
    "a&": {
      "&:hover": {
        color: Color(theme.colors.error).lighten(.25).hex(),
      },
      "&:focus": {
        color: theme.colors.error,
        textDecoration: "none",
      },
    },
  })

  return disabled ?
    (
      <span className={cx(styles, css({
        color: Color(theme.colors.error).desaturate(.5).lighten(.1).hex(),
      }))}
      >
        {nodeId}
      </span>
    ) :
    (
      <Link
        className={styles}
        to={`?nodeId=${nodeId}`}
        onClick={onClick}
      >
        {nodeId}
      </Link>
    )
}
