import * as MatomoTracker from "matomo-tracker"
import SystemUtils from "../common/SystemUtils"
import AnalyticsEngine from "./AnalyticsEngine"

export default class MatomoAnalytics extends AnalyticsEngine {

  constructor(analyticsSettings) {
    super()
    _.assign(this, _.pick(analyticsSettings, ["siteId", "url"]))
  }

  sendEvent = (event) => {
    new MatomoTracker(Number(this.siteId), this.url).track({
      _id: SystemUtils.getUserId(),
      url: window.location.href,
      ...event.event,
    })
  }
}
