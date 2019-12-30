import React from "react"
import PropTypes from 'prop-types'
import ProcessStateUtils from '../../common/ProcessStateUtils'
import {connect} from "react-redux"
import ActionsUtils from "../../actions/ActionsUtils"

export class ListStateComponent extends React.Component {
  iconStyles = {
    width: 24,
    height: 24,
    margin: '0 auto'
  }

  properties = {
    TOOLTIPS: "tooltips",
    ICONS: "icons"
  }

  getSettingsTooltip = (process, state) => this.getSettingsProperty(this.properties.TOOLTIPS, process, state)

  getSettingsIcon = (process, state) => this.getSettingsProperty(this.properties.ICONS, process, state)

  getSettingsProperty = (property, process, state) => {
    const status = ProcessStateUtils.getProcessStateStatus(process, state)
    const settings =  _.get(this.props.processStatesSettings, property)
    const engineConfig = _.get(settings, process.processingType)
    return _.get(engineConfig, status)
  }

  prepareTooltip = (process, state, isStateLoaded) => {
    if (isStateLoaded === false || process.deployment == null) {
      return this.getTooltip(process, {'status': ProcessStateUtils.getProcessStatus(process)}, isStateLoaded)
    }

    //TODO: are we sure that?
    if (ProcessStateUtils.isDeployed(process) && !ProcessStateUtils.isRunning(state)) {
      return this.getTooltip(process, {'status': ProcessStateUtils.STATUSES.FAILED}, true)
    }

    return this.getTooltip(process, state, true)
  }

  getTooltip = (process, state, isStateLoaded) => {
    const tooltip = this.getSettingsTooltip(process, state) || ProcessStateUtils.getStateTooltip(state)
    return tooltip  + (isStateLoaded === false ? " Loading current state of the process..." : "")
  }

  prepareIcon = (process, state, isStateLoaded) => {
    if (isStateLoaded === false || process.deployment == null) {
      return this.getIcon(process, {'status': ProcessStateUtils.getProcessStatus(process)})
    }

    //TODO: are we sure that?
    if (ProcessStateUtils.isDeployed(process) && !ProcessStateUtils.isRunning(state)) {
      return this.getIcon(process, {'status': ProcessStateUtils.STATUSES.FAILED})
    }

    return this.getIcon(process, state)
  }

  getIcon = (process, state) => this.getSettingsIcon(process, state) || ProcessStateUtils.getStateIcon(state)

  render() {
    const {process, state, isStateLoaded} = this.props
    return <div
      dangerouslySetInnerHTML={{__html: this.prepareIcon(process, state, isStateLoaded) }}
      title={this.prepareTooltip(process, state, isStateLoaded)}
      style={this.iconStyles}
      className={isStateLoaded ? 'list-state centered-column' : 'status-pending list-state centered-column'}
    />
  }
}

ListStateComponent.propTypes = {
  processStatesSettings: PropTypes.object.isRequired,
  process: PropTypes.object.isRequired,
  isStateLoaded: PropTypes.bool,
  state: PropTypes.object,
}

ListStateComponent.defaultProps = {
  isStateLoaded: false,
  state: undefined,
}

const mapState = state => ({
  processStatesSettings: state.settings.processStatesSettings
})

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ListStateComponent)
