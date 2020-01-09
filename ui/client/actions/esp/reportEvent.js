export function reportEvent(eventInfo) {
  return (dispatch) => {
    return dispatch({
      type: "USER_TRACKING",
      tracking: {
        event: {
          e_c: eventInfo.category,
          e_a: eventInfo.action,
          e_n: eventInfo.name,
        },
      },
    })
  }
}
