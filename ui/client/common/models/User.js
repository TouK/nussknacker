import * as _ from "lodash"

const User = function User(data) {
  this.permissions = _.uniq(_.flatMap(data.categoryPermissions))
  this.categoryPermissions = data.categoryPermissions
  this.categories = data.categories
  this.isAdmin = data.isAdmin
  this.globalPermissions = data.globalPermissions
  this.id = data.id
}

User.prototype.hasPermission = function (permission, category) {
  let permissions = this.categoryPermissions[category] || []
  return this.isAdmin || category && permissions.includes(permission)
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

User.prototype.isWriter = function () {
  return this.isAdmin || this.permissions.includes("Write")
}

export default User