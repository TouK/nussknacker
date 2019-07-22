import React from "react"

class PeriodicallyReloadingComponent extends React.Component {
  baseIntervalTime = 10000
  intervalTime = null
  intervalId = null

  reload() {
    if (this.method === undefined) {
      throw new TypeError("Must override method")
    }
  }

  //Default or you can override this..
  getIntervalTime() {
    return this.intervalTime || this.baseIntervalTime
  }

  onMount() {
    //to be overridden
  }

  componentDidMount() {
    this.onMount()
    this.intervalId = setInterval(() => this.reload(), this.getIntervalTime())
  }

  componentWillUnmount() {
    if (this.intervalId) {
      clearInterval(this.intervalId)
      this.intervalId = null
    }
  }
}

export default PeriodicallyReloadingComponent
