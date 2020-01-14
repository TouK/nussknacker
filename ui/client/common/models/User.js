/* eslint-disable i18next/no-literal-string */
// @flow
import {flatMap, uniq} from "lodash"

type Permission = "Read" | "Write" | "Deploy"
type PermissionCategory = string
type CategoryPermissions = { [category: PermissionCategory]: Permission[] }
type GlobalPermissions = { [key: string]: boolean }

export type UserData = {
  permissions: Permission[],
  categoryPermissions: CategoryPermissions,
  categories: PermissionCategory[],
  isAdmin: boolean,
  globalPermissions: GlobalPermissions,
  id: string,
}

export default class User {
  categories: PermissionCategory[]
  categoryPermissions: CategoryPermissions
  globalPermissions: GlobalPermissions
  permissions: Permission[]
  isAdmin: boolean
  id: string

  constructor({categories, categoryPermissions, globalPermissions, id, isAdmin}: UserData) {
    this.categoryPermissions = categoryPermissions
    this.categories = categories
    this.isAdmin = isAdmin
    this.globalPermissions = globalPermissions
    this.id = id
    this.permissions = uniq(flatMap(categoryPermissions))
  }

  hasPermission(permission: Permission, category: PermissionCategory) {
    if (this.isAdmin) {
      return true
    }
    const permissions = this.categoryPermissions[category] || []
    return permissions.includes(permission)
  }

  canRead(category: PermissionCategory) {
    return this.hasPermission("Read", category)
  }

  canDeploy(category: PermissionCategory) {
    return this.hasPermission("Deploy", category)
  }

  canWrite(category: PermissionCategory) {
    return this.hasPermission("Write", category)
  }

  isWriter() {
    return this.isAdmin || this.permissions.includes("Write")
  }
}