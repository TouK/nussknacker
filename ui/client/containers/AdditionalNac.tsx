import PropTypes, {Validator} from "prop-types"
import React from "react"
import {connect} from "react-redux"

export class AdditionalNac extends React.Component {

  static path: string
  static header: string
  static propTypes: { settings: Validator<NonNullable<object>> }

  private readonly id: number

  constructor(props) {
    super(props)

    const {match: {params: {id}}} = props
    this.id = id
  }

  render() {
    const tab = this.props.settings[this.id]
    const ref = "metricsFrame"
    if (tab) {
      return (
        <div className="Page">
          <iframe
            ref={ref}
            src={tab.url}
            width="100%"
            height={window.innerHeight}
            frameBorder="0"
          />
        </div>
      )
    }

    return (<div/>)
  }
}

AdditionalNac.propTypes = {
  settings: PropTypes.array.isRequired,
  match: PropTypes.object.isRequired,
}

AdditionalNac.path = "/additionalnac"
AdditionalNac.header = "additionalnac"

function mapState(state) {
  return {
    settings: state.settings.featuresSettings.additionalNac || [],
  }
}

export default connect(mapState)(AdditionalNac)
