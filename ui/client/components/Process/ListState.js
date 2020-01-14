import PropTypes from "prop-types"
import React from "react"
import {CSSTransition, SwitchTransition} from "react-transition-group"
import ProcessStateUtils from "../../common/ProcessStateUtils"

export default class ListState extends React.Component {

  animationTimeout = {
    enter: 500,
    appear: 500,
    exit: 500,
  }

  animationListener = (node, done) => node.addEventListener("transitionend", done, false)

  getTooltip = (process, state, isStateLoaded) => {
    if (isStateLoaded === false || process.deployment == null && state == null) {
      return ProcessStateUtils.getProcessTooltip(process, isStateLoaded)
    }

    //TODO: are we sure that?
    if (ProcessStateUtils.isDeployed(process) && !ProcessStateUtils.isRunning(state)) {
      return ProcessStateUtils.getStatusTooltip(ProcessStateUtils.STATUSES.ERROR)
    }

    return ProcessStateUtils.getStateTooltip(state)
  }

  getIcon = (process, state, isStateLoaded) => {
    if (isStateLoaded === false || process.deployment == null && state == null) {
      return ProcessStateUtils.getProcessIcon(process)
    }

    //TODO: are we sure that?
    if (ProcessStateUtils.isDeployed(process) && !ProcessStateUtils.isRunning(state)) {
      return ProcessStateUtils.getStatusIcon(ProcessStateUtils.STATUSES.ERROR)
    }

    return ProcessStateUtils.getStateIcon(state)
  }

  render() {
    const {process, state, isStateLoaded} = this.props
    const icon = this.getIcon(process, state, isStateLoaded)
    const tooltip = this.getTooltip(process, state, isStateLoaded)
    const iconClass = `state-list${isStateLoaded === false ? " state-pending" : ""}`
    const transitionKey = `${process.id}-${icon}`

    return (
      <SwitchTransition>
        <CSSTransition key={transitionKey} classNames="fade" timeout={this.animationTimeout} addEndListener={this.animationListener}>
          <img src={icon} title={tooltip} alt={tooltip} className={iconClass}/>
        </CSSTransition>
      </SwitchTransition>
    )
  }
}

ListState.propTypes = {
  process: PropTypes.object.isRequired,
  isStateLoaded: PropTypes.bool,
  state: PropTypes.object,
}

ListState.defaultProps = {
  isStateLoaded: false,
  state: undefined,
}
