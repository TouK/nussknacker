import {userId} from "../containers/EspApp"

const matomoTracker = () => {
  const MatomoTracker = require('matomo-tracker')
  return MatomoTracker(2, "http://localhost:80/matomo.php")
}

function track(trackingMetadata) {
  matomoTracker().track({
    _id: localStorage.getItem(userId),
    url: window.location.href,
    ...trackingMetadata.event
  });
}

export const userTracker = (state) => next => action => {
  if (!_.isEmpty(action.tracking)) {
    track(action.tracking)
  }
  return next(action)
}
