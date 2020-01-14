/* eslint-disable i18next/no-literal-string */
import {flatMap, uniq} from "lodash"

type Permission = "Read" | "Write" | "Deploy"
type PermissionCategory = string
type CategoryPermissions = Record<PermissionCategory, Permission[]>
type GlobalPermissions = Record<string, boolean>

export type UserData = {
  permissions: Permission[];
  categoryPermissions: CategoryPermissions;
  categories: PermissionCategory[];
  isAdmin: boolean;
  globalPermissions: GlobalPermissions;
  id: string;
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

  hasPermission(permission: Permission, category: PermissionCategory): boolean {
    if (this.isAdmin) {
      return true
    }
    const permissions = this.categoryPermissions[category] || []
    return permissions.includes(permission)
  }

  canRead(category: PermissionCategory): boolean {
    return this.hasPermission("Read", category)
  }

  canDeploy(category: PermissionCategory): boolean {
    return this.hasPermission("Deploy", category)
  }

  canWrite(category: PermissionCategory): boolean {
    return this.hasPermission("Write", category)
  }

  isWriter(): boolean {
    return this.isAdmin || this.permissions.includes("Write")
  }
}
