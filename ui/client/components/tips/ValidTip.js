import React from 'react';
import HeaderIcon from "./HeaderIcon";
import HeaderTitle from "./HeaderTitle";

export default class ValidTip extends React.Component {

  render() {
    const {icon, message} = this.props
    return (
      <div className={"valid-tip"}>
        <HeaderIcon className={"icon"} icon={icon}/>
        <HeaderTitle message={message}/>
      </div>
    )
  }
}