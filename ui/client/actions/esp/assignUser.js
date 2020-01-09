import User from "../../common/models/User"

export function assignUser(data) {
  return {
    type: "LOGGED_USER",
    user: new User(data),
  }
}
