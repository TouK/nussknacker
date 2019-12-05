import SystemUtils from "../../common/SystemUtils";

export function matomoConfigured(analyticsSettings) {
  return !_.isEmpty(analyticsSettings)
    && analyticsSettings.engine === 'Matomo'
    && !_.isEmpty(analyticsSettings.url)
    && !_.isEmpty(analyticsSettings.siteId)
}

export function track(event, analyticsSettings) {
  const MatomoTracker = require('matomo-tracker');
  MatomoTracker(Number(analyticsSettings.siteId), analyticsSettings.url).track({
    _id: SystemUtils.getUserId(),
    url: window.location.href,
    ...event.event
  });
}