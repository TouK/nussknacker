import React from "react";
import {v4 as uuid4} from "uuid";

export default class ValidationLabels extends React.Component {

  render() {
    const {validators, value} = this.props
    return validators.map(validator => validator.isValid(value) ? null :
      <span className='validation-label'>{validator.message}</span>)
  }
}