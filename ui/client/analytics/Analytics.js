import MatomoAnalytics from "./MatomoAnalytics"

const MATOMO_ENGINE = "Matomo"

export default class Analytics {

  constructor(analyticsSettings) {
    if (analyticsSettings.engine === MATOMO_ENGINE) {
      this.engine = new MatomoAnalytics(analyticsSettings)
    }
  }

  sendEvent = (event) => this.engine.sendEvent(event)
}