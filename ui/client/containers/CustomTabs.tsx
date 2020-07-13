import PropTypes, {Validator} from "prop-types"
import React from "react"
import {connect} from "react-redux"

export class CustomTabs extends React.Component<{ settings: $TodoType[], match: $TodoType }> {

  static path: string
  static header: string
  static propTypes: {
    settings: Validator<NonNullable<any[]>>,
    match: Validator<NonNullable<object>>,
  }

  constructor(props) {
    super(props)
  }

  render() {
    const {match: {params: {id}}}  = this.props
    const ref = "customTabsFrame"
    const tab = this.props.settings
      .find(o => o.id == id)

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

CustomTabs.propTypes = {
  settings: PropTypes.array.isRequired,
  match: PropTypes.object.isRequired,
}

CustomTabs.path = "/customtabs"
CustomTabs.header = "customtabs"

function mapState(state) {
  return {
    settings: state.settings.featuresSettings.customTabs || [],
  }
}

export default connect(mapState)(CustomTabs)
