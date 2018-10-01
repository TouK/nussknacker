import React from "react";
import {connect} from "react-redux";
import _ from "lodash";
import ActionsUtils from "../../actions/ActionsUtils";
import "../../stylesheets/visualization.styl";
import GenericModalDialog from "./GenericModalDialog";
import Dialogs from "./Dialogs"
import HttpService from "../../http/HttpService";
import Moment from "moment"
import DatePicker from 'react-datepicker';
import 'react-datepicker/dist/react-datepicker.css';
import '../../stylesheets/datePicker.styl'


class CalculateCountsDialog extends React.Component {

  dateFormat = "YYYY-MM-DD HH:mm:ss";

  predefinedRanges = [
    {
      name: "Last hour",
      from: () => Moment().subtract(1, 'hours'),
      to: () => Moment()
    },
    {
      name: "Today",
      from: () => Moment().startOf('day'),
      to: () => Moment()
    },
    {
      name: "Yesterday",
      from: () => Moment().subtract(1, 'days').startOf('day'),
      to: () => Moment().startOf('day')
    },
    {
      name: "Day before yesterday",
      from: () => Moment().subtract(2, 'days').startOf('day'),
      to: () => Moment().subtract(1, 'days').startOf('day')
    },
    {
      name: "This day last week",
      from: () => Moment().subtract(8, 'days').startOf('day'),
      to: () => Moment().subtract(7, 'days').startOf('day')
    }
  ]

  setTime(range) {
    this.setState({
      processCountsDateFrom: range.from(),
      processCountsDateTo: range.to(),
    })
  }

  constructor(props) {
    super(props);
    const nowMidnight = Moment().startOf('day')
    const yesterdayMidnight = Moment().subtract(1, 'days').startOf('day')
    this.initState = {
      processCountsDateFrom: yesterdayMidnight,
      processCountsDateTo: nowMidnight
    };
    this.state = this.initState
  }

  confirm = () => {
    return HttpService.fetchProcessCounts(this.props.processId,
      this.state.processCountsDateFrom.format(this.dateFormat),
      this.state.processCountsDateTo.format(this.dateFormat))
      .then((processCounts) => this.props.actions.displayProcessCounts(processCounts))
  }

  render() {
    return (
      <GenericModalDialog init={() => this.setState(this.initState)}
                          confirm={this.confirm} type={Dialogs.types.calculateCounts}>
        <p>Process counts from</p>
        <div className="datePickerContainer">
          <DatePicker
            selected={this.state.processCountsDateFrom}
            showTimeSelect
            timeFormat="HH:mm"
            timeIntervals={15}
            dateFormat={this.dateFormat}
            onChange={(e) => this.setState({processCountsDateFrom: e})}
          />
        </div>
        <p>Process counts to</p>
        <div className="datePickerContainer">
          <DatePicker
            selected={this.state.processCountsDateTo}
            showTimeSelect
            timeFormat="HH:mm"
            timeIntervals={15}
            dateFormat={this.dateFormat}
            onChange={(e) => this.setState({processCountsDateTo: e})}
          />
        </div>
        <p>Quick ranges</p>
        {
          this.predefinedRanges.map(range =>
            (<button type="button" key={range.name} title={range.name} className='predefinedRangeButton' onClick={() => this.setTime(range)}>{range.name}</button>)
          )
        }
      </GenericModalDialog>
    );
  }
}

function mapState(state) {
  return {
    processId: _.get(state.graphReducer, 'fetchedProcessDetails.id'),
    processToDisplay: state.graphReducer.processToDisplay
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(CalculateCountsDialog);


