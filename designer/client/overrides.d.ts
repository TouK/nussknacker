/* eslint-disable i18next/no-literal-string */
// overrides bugs in typings

import * as React from "react"

declare module "react-bootstrap/lib/Panel" {
  interface PanelProps {
    bsClass?: string,
  }

  export class Panel extends React.Component<PanelProps> {}
}

declare module "react-select" {
  export {defaultTheme} from "react-select/src/theme"
}
