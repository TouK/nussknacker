import classNames from "classnames"
import _ from "lodash"
import Moment from "moment"
import React from "react"
import DateTimePicker from "react-datetime"
import {connect} from "react-redux"
import ActionsUtils from "../../actions/ActionsUtils"
import {dateFormat} from "../../config"
import "../../stylesheets/visualization.styl"
import {ButtonWithFocus} from "../withFocus"
import Dialogs from "./Dialogs"
import GenericModalDialog from "./GenericModalDialog"

class CalculateCountsDialog extends React.Component {
  dateFormat="YYYY-MM-DD" // eslint-disable-line i18next/no-literal-string
  timeFormat="HH:mm:ss" // eslint-disable-line i18next/no-literal-string

  predefinedRanges = [
    {
      name: "Last hour",
      from: () => Moment().subtract(1, "hours").toDate(),
      to: () => Moment().toDate(),
    },
    {
      name: "Today",
      from: () => Moment().startOf("day").toDate(),
      to: () => Moment().toDate(),
    },
    {
      name: "Yesterday",
      from: () => Moment().subtract(1, "days").startOf("day").toDate(),
      to: () => Moment().startOf("day").toDate(),
    },
    {
      name: "Day before yesterday",
      from: () => Moment().subtract(2, "days").startOf("day").toDate(),
      to: () => Moment().subtract(1, "days").startOf("day").toDate(),
    },
    {
      name: "This day last week",
      from: () => Moment().subtract(8, "days").startOf("day").toDate(),
      to: () => Moment().subtract(7, "days").startOf("day").toDate(),
    },
  ]

  setTime(range) {
    this.setState({
      processCountsDateFrom: range.from(),
      processCountsDateTo: range.to(),
    })
  }

  constructor(props) {
    super(props)
    const nowMidnight = Moment().startOf("day")
    const yesterdayMidnight = Moment().subtract(1, "days").startOf("day")
    this.initState = {
      processCountsDateFrom: yesterdayMidnight.toDate(),
      processCountsDateTo: nowMidnight.toDate(),
    }
    this.state = this.initState
  }

  confirm = () => this.props.actions.fetchAndDisplayProcessCounts(
    this.props.processId,
    Moment(this.state.processCountsDateFrom),
    Moment(this.state.processCountsDateTo),
  );

  setRawDate = (date, stateChange) => {
    stateChange(Moment(date, dateFormat))
  };

  setDateFrom(date) {
    this.setState((state, props) => ({processCountsDateFrom: date}))
  }

  setDateTo(date) {
    this.setState((state, props) => ({processCountsDateTo: date}))
  }

  datePickerStyle = {
    className: classNames([
      "node-input",
    ]),
  }

  render() {
    return (
      <GenericModalDialog
        init={() => this.setState(this.initState)}
        confirm={this.confirm}
        type={Dialogs.types.calculateCounts}
      >
        <p>Process counts from</p>
        <div className="datePickerContainer">
          <DateTimePicker
            onChange={this.setDateFrom.bind(this)}
            dateFormat={this.dateFormat}
            timeFormat={this.timeFormat}
            value={this.state.processCountsDateFrom}
            inputProps={this.datePickerStyle}
          />

        </div>
        <p>Process counts to</p>
        <div className="datePickerContainer">
          <DateTimePicker
            onChange={this.setDateTo.bind(this)}
            dateFormat={this.dateFormat}
            timeFormat={this.timeFormat}
            value={this.state.processCountsDateTo}
            inputProps={this.datePickerStyle}

          />
        </div>
        <p>Quick ranges</p>
        {
          this.predefinedRanges.map(range => (
            <ButtonWithFocus
              type="button"
              key={range.name}
              title={range.name}
              className="predefinedRangeButton"
              onClick={() => this.setTime(range)}
            >{range.name}</ButtonWithFocus>
          ))
        }
      </GenericModalDialog>
    )
  }
}

function mapState(state) {
  return {
    processId: _.get(state.graphReducer, "fetchedProcessDetails.id"),
    processToDisplay: state.graphReducer.processToDisplay,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(CalculateCountsDialog)

