//Developers can handle error on two ways:
//1. Catching it at HttpService and show error information modal - application still works normally
//2. Catching it at Containers / etc.. and show ErrorPage by run action handleHTTPError - application stop works
export function handleHTTPError(error) {
  return {
    type: "HANDLE_HTTP_ERROR",
    error: error,
  }
}
