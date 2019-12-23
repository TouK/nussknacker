import React from "react"
import PropTypes from 'prop-types'
import ProcessStateUtils from '../../common/ProcessStateUtils'
import {withRouter} from "react-router-dom"
import {connect} from "react-redux"
import ActionsUtils from "../../actions/ActionsUtils"

class ListState extends React.Component {
  iconStyles = {
    width: 24,
    height: 24,
    margin: '0 auto'
  }

  getSettingsProperty = (property, process, state) => {
    const status = ProcessStateUtils.getStateStatus(state)
    const settings =  _.get(this.props.processStatesSettings, property)
    const engineConfig = _.get(settings, process.processingType)
    return _.get(engineConfig, status)
  }

  getTooltip = (process, state, isStateLoaded) => {
    if (isStateLoaded === false || process.deployment == null) {
      return ProcessStateUtils.getProcessTooltip(process, isStateLoaded)
    }

    return this.getSettingsProperty("tooltips", process, state) || ProcessStateUtils.getStateTooltip(state)
  }

  getIcon = (process, state, isStateLoaded) => {
    if (isStateLoaded === false || process.deployment == null) {
      return ProcessStateUtils.getProcessIcon(process)
    }

    return this.getSettingsProperty("icons", process, state) || ProcessStateUtils.getStateIcon(state)
  }

  render() {
    const {process, state, isStateLoaded} = this.props

    return <div
      dangerouslySetInnerHTML={{__html: this.getIcon(process, state, isStateLoaded) }}
      title={this.getTooltip(process, state, isStateLoaded)}
      style={this.iconStyles}
      className={isStateLoaded ? 'centered-column' : 'status-pending centered-column'}
    />
  }
}

ListState.propTypes = {
  process: PropTypes.object.isRequired,
  isStateLoaded: PropTypes.bool,
  state: PropTypes.object,
}

ListState.defaultProps = {
  isStateLoaded: false
}

const mapState = state => ({
  processStatesSettings: state.settings.processStatesSettings
})

export default withRouter(connect(mapState, ActionsUtils.mapDispatchWithEspActions)(ListState))
