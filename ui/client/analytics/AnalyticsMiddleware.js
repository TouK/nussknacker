import Analytics from "./Analytics"

export const analyticsMiddleware = ({dispatch, getState}) => next => action => {
  if (_.isFunction(action)) {
    return action(dispatch, getState)
  }

  const state = !_.isEmpty(getState()) ? getState() : null
  const analyticsSettings = _.get(state, "settings.analyticsSettings")
  if (!_.isEmpty(analyticsSettings) && actionTracked(action)) {
    analytics(analyticsSettings).sendEvent(action.tracking)
  }
  return next(action)
}

const actionTracked = action => !_.isEmpty(action)
  && !_.isEmpty(action.tracking)

const analytics = (analyticsSettings) => new Analytics(analyticsSettings)
