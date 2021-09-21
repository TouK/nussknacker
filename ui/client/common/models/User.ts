/* eslint-disable i18next/no-literal-string */
import {flatMap, uniq} from "lodash"

type Permission = "Read" | "Write" | "Deploy" | "Demo"
type PermissionCategory = string
type CategoryPermissions = Record<PermissionCategory, Permission[]>
type GlobalPermissions = string[]

export type UserData = {
  permissions: Permission[],
  categoryPermissions: CategoryPermissions,
  categories: PermissionCategory[],
  isAdmin: boolean,
  globalPermissions: GlobalPermissions,
  id: string,
}

export default class User {
  readonly categories: PermissionCategory[]
  private readonly categoryPermissions: CategoryPermissions
  private readonly globalPermissions: GlobalPermissions
  private permissions: Permission[]
  private readonly isAdmin: boolean
  readonly id: string

  constructor({categories, categoryPermissions, globalPermissions, id, isAdmin}: UserData) {
    this.categoryPermissions = categoryPermissions
    this.categories = categories
    this.isAdmin = isAdmin
    this.globalPermissions = globalPermissions
    this.id = id
    this.permissions = uniq(flatMap(categoryPermissions))
  }

  private hasPermission(permission: Permission, category: PermissionCategory): boolean {
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

  //user can edit graph on FE, but cannot save changes to BE. This can be useful e.g. for demo purposes
  canEditFrontend(category: PermissionCategory): boolean {
    return this.hasPermission("Demo", category) || this.hasPermission("Write", category)
  }

  isWriter(): boolean {
    return this.isAdmin || this.permissions.includes("Write")
  }

  hasGlobalPermission(name: string): boolean {
    return this.isAdmin || this.globalPermissions.includes(name)
  }
}
