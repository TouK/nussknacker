import _ from "lodash"
import React from "react"
import {connect} from "react-redux"
import ActionsUtils from "../actions/ActionsUtils"
import QueriedStateTable from "../components/QueriedStateTable"
import {InputWithFocus, SelectWithFocus} from "../components/withFocus"
import HttpService from "../http/HttpService"
import i18next from "i18next"

//this needs some love
export class Signals extends React.Component {

  constructor(props) {
    super(props)
    this.state = this.initialState(props)
  }

  componentDidMount() {
    HttpService.fetchSignals().then(response => {
      const signals = response.data
      const firstSignal = _.head(_.keys(signals))
      this.setState({
        signals: signals,
        signalType: firstSignal,
        processId: signals[firstSignal].availableProcesses[0],
      })
    })
  }

  initialState(props) {
    return {signalType: "", processId: null, signalParams: {}, signals: {}}
  }

  render() {
    const currentSignal = this.findSignal(this.state.signalType)
    let sendSignalButtonTooltip
    if (_.isEmpty(this.state.signalType)) {
      sendSignalButtonTooltip = i18next.t("signals.notSelected.tooltip.type", "Signal type is not selected")
    } else if (_.isEmpty(this.state.processId)) {
      sendSignalButtonTooltip = i18next.t("signals.notSelected.tooltip.scenario", "Scenario is not selected")
    }
    //fixme simplify this view as in QueriedStateTable
    return (
      <div className="full-dark">
        <div className="modalContentDark">
          <div className="node-table">
            <div className="node-table-body">
              <div className="node-row">
                <div className="node-label">{i18next.t("signals.type", "Signal type")}</div>
                <div className="node-value">
                  <SelectWithFocus
                    className="node-input"
                    onChange={(e) => {
                      const nextSignalType = e.target.value
                      this.setState({
                        signalType: nextSignalType,
                        signalParams: {},
                        processId: this.firstProcessForSignal(nextSignalType),
                      })
                    }}
                  >
                    {_.map(_.keys(this.state.signals), (sig, index) => (
                      <option key={index} value={sig}>{sig}</option>))}
                  </SelectWithFocus>
                </div>
              </div>
              <div className="node-row">
                <div className="node-label">{i18next.t("signals.scenario", "Scenario")}</div>
                <div className="node-value">
                  <SelectWithFocus className="node-input" onChange={(e) => this.setState({processId: e.target.value})}>
                    {(currentSignal.availableProcesses || [])
                      .map((process, index) => (<option key={index} value={process}>{process}</option>))}
                  </SelectWithFocus>
                </div>
              </div>
              {_.get(currentSignal, "parameters", []).map((param, idx) => {
                return (
                  <div className="node-row" key={idx}>
                    <div className="node-label">{param}</div>
                    <div className="node-value">
                      <InputWithFocus
                        className="node-input"
                        type="text"
                        value={this.state.signalParams[param] || ""}
                        onChange={(e) => this.changeParamValue(param, e.target.value)}
                      />
                    </div>
                  </div>
                )
              })}
            </div>
            <button
              type="button"
              className="modalButton"
              disabled={_.isEmpty(this.state.signalType) || _.isEmpty(this.state.processId)}
              title={sendSignalButtonTooltip}
              onClick={this.sendSignal.bind(this, this.state.signalType, this.state.processId, this.state.signalParams)}
            >{i18next.t("signals.send.button", "Send signal")}</button>
          </div>
        </div>
        <hr/>
        <QueriedStateTable/>
      </div>
    )
  }

  firstProcessForSignal = (signalType) => {
    const signalForType = this.findSignal(signalType)
    return _.head(signalForType.availableProcesses)
  }

  findSignal = (signalType) => {
    return this.state.signals[signalType] || {}
  }

  sendSignal = (signalType, processId, signalParams) => {
    return HttpService.sendSignal(signalType, processId, signalParams)
  }

  changeParamValue = (paramName, newValue) => {
    const newSignalParams = _.cloneDeep(this.state.signalParams)
    _.set(newSignalParams, paramName, newValue)
    this.setState({signalParams: newSignalParams})
  }

}

Signals.path = `/signals`
Signals.header = "Signals"

function mapState(state) {
  return {
    processingType: "streaming",
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(Signals)
