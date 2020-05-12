/* eslint-disable i18next/no-literal-string */
declare const __DEV__: boolean
declare const __GIT__: {
  HASH: string,
  DATE: string,
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare type $TodoType = any

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

declare module "*.html" {
  const content: string
  export default content
}

declare module "*.svg" {
  const content: string
  export const ReactComponent: React.FC<React.SVGProps<SVGSVGElement>>
  export default content
}

declare module "!raw-loader!*" {
  const content: string
  export default content
}
