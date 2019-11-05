import React from "react";

export default class ValidationLabels extends React.Component {

  render() {
    const {validators, values} = this.props
    return validators.map(validator => validator.isValid(...values) ? null :
      <span className='validation-label'>{validator.message}</span>)
  }
}