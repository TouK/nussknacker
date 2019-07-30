import * as _ from "lodash"

const User = function User(data) {
  _.defaultsDeep(this, data)
}

User.prototype.hasPermission = function (permission, category) {
  let permissions = this.categoryPermissions[category] || []
  return category && permissions.includes(permission)
}

User.prototype.canRead = function (category) {
  return this.hasPermission("Read", category)
}

User.prototype.canDeploy = function (category) {
  return this.hasPermission("Deploy", category)
}

User.prototype.canWrite = function (category) {
  return this.hasPermission("Write", category)
}

User.prototype.isReader = function () {
  return this.permissions.includes("Read")
}

User.prototype.isDeployer = function () {
  return this.permissions.includes("Deploy")
}

User.prototype.isWriter = function () {
  return this.permissions.includes("Write")
}

User.prototype.isAdmin = function () {
  return this.permissions.includes("Admin")
}

export default User