/* eslint-disable i18next/no-literal-string */
declare const __DEV__: boolean
declare const __GIT__: {
  HASH: string,
  DATE: string,
}

declare type $TodoType = unknown

declare module "*.css" {
  const classes: { [key: string]: string }
  export default classes
}

declare module "*.styl" {
  const classes: { [key: string]: string }
  export default classes
}

declare module "*.less" {
  const classes: { [key: string]: string }
  export default classes
}
