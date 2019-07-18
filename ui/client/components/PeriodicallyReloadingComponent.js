import React from "react";


class PeriodicallyReloadingComponent extends React.Component {
  baseIntervalTime = 40000
  intervalTime = null
  intervalId = null

  reload() {
    if (this.method === undefined) {
      throw new TypeError("Must override method")
    }
  }

  onMount() {
    //to be overridden
  }

  componentDidMount() {
    this.onMount()
    this.intervalId = setInterval(() => this.reload(), this.getIntervalTime() || this.baseIntervalTime)
  }

  componentWillUnmount() {
    if (this.intervalId) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
  }
}

export default PeriodicallyReloadingComponent
