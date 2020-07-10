import PropTypes, {Validator} from "prop-types"
import React from "react"
import {connect} from "react-redux"

export class DynamicTabs extends React.Component<{ settings: $TodoType[] }> {

  static path: string
  static header: string
  static propTypes: {
    settings: Validator<NonNullable<any[]>>,
    match: Validator<NonNullable<object>>,
  }

  private readonly id: string

  constructor(props) {
    super(props)

    const {match: {params: {id}}}  = props
    this.id = id
  }

  render() {
    const ref = "dynamicTabsFrame"
    const tab = this.props.settings
      .find(o => o.id == this.id)

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

DynamicTabs.propTypes = {
  settings: PropTypes.array.isRequired,
  match: PropTypes.object.isRequired,
}

DynamicTabs.path = "/dynamictabs"
DynamicTabs.header = "dynamictabs"

function mapState(state) {
  return {
    settings: state.settings.featuresSettings.dynamicTabs || [],
  }
}

export default connect(mapState)(DynamicTabs)
