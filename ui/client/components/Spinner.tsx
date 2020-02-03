import React from "react"
import "../stylesheets/spinner.styl"

type State = {}

type Props = {
  show: boolean,
}

export default class LoaderSpinner extends React.Component<Props, State> {
  render() {
    return !this.props.show ? null : (
      <div className="preloader-spin">
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
        <div className="preloader-spin__petal"/>
      </div>
    )
  }
}
