import React from "react"
import {v4 as uuid4} from "uuid"

export default class ValidationLabels extends React.Component {

  render() {
    const {validators, values} = this.props
    return (
      <div className={"validation-labels"}>
        {validators.map(validator => validator.isValid(...values) ?
          null : <span key={uuid4()} className="validation-label" title={validator.description}>{validator.message}</span>)}
      </div>
    )
  }
}
