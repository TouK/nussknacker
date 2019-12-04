import React from 'react';
import {v4 as uuid4} from "uuid";
import PropTypes from "prop-types"
import InlinedSvgs from "../../assets/icons/InlinedSvgs";
import HeaderTitle from "./HeaderTitle";
import HeaderIcon from "./HeaderIcon";

export default class ValidTip extends React.Component {

  static propTypes = {
    grouping: PropTypes.bool.isRequired,
    testing: PropTypes.bool.isRequired
  }

  render() {
    return (
      <div key={uuid4()}>{this.validTip()}</div>
    )
  }

  validTip = () => {
    const {grouping, testing} = this.props

    if (testing) {
      return <HeaderTitle message={"Testing mode enabled"}/>
    } else if (grouping) {
      return <HeaderTitle message={"Grouping mode enabled"}/>
    } else {
      return <React.Fragment>
               <HeaderIcon className={"icon"} icon={InlinedSvgs.tipsSuccess}/>
               <HeaderTitle message={"Everything seems to be OK"}/>
             </React.Fragment>
    }
  }
}