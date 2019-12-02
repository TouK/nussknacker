import React from 'react';
import {v4 as uuid4} from "uuid";
import PropTypes from "prop-types"

export default class ValidTip extends React.Component {

  static propTypes = {
    grouping: PropTypes.bool.isRequired,
    testing: PropTypes.bool.isRequired
  }

  render() {
    return (
      <div key={uuid4()}><span>{this.validTip()}</span></div>
    )
  }

  validTip = () => {
    const {grouping, testing} = this.props

    if (testing) {
      return "Testing mode enabled"
    } else if (grouping) {
      return "Grouping mode enabled"
    } else {
      return "Everything seems to be OK"
    }
  }
}