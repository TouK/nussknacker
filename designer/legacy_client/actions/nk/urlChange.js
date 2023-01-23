import {handleHTTPError} from "./errors"

export function urlChange(location) {

  return (dispatch) => {
    dispatch(handleHTTPError(null))

    dispatch({
      type: "URL_CHANGED",
      location: location,
    })
  }
}
