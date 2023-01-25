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

//FIXME: remove class from store - plain data only
export default class User {
  readonly categories: PermissionCategory[]
  readonly id: string
  private readonly categoryPermissions: CategoryPermissions
  private readonly globalPermissions: GlobalPermissions
  private permissions: Permission[]
  private readonly isAdmin: boolean

  constructor({categories, categoryPermissions, globalPermissions, id, isAdmin}: UserData) {
    this.categoryPermissions = categoryPermissions
    this.categories = categories
    this.isAdmin = isAdmin
    this.globalPermissions = globalPermissions
    this.id = id
    this.permissions = uniq(flatMap(categoryPermissions))
  }

  canRead(category: PermissionCategory): boolean {
    return this.hasPermission("Read", category)
  }

  isWriter(): boolean {
    return this.isAdmin || this.permissions.includes("Write")
  }

  private hasPermission(permission: Permission, category: PermissionCategory): boolean {
    if (this.isAdmin) {
      return true
    }
    const permissions = this.categoryPermissions[category] || []
    return permissions.includes(permission)
  }

}
