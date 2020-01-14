import User, {UserData} from "../../common/models/User"

export type LoggedUserAction = {
  type: "LOGGED_USER";
  user: User;
}

export function assignUser(data: UserData): LoggedUserAction {
  return {
    type: "LOGGED_USER",
    user: new User(data),
  }
}

export default assignUser
