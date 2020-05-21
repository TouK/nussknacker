/* eslint-disable i18next/no-literal-string */
// overrides bugs in typings

import * as React from "react"
import * as Backbone from "backbone"

declare module "react-bootstrap/lib/Panel" {
  interface PanelProps {
    bsClass?: string,
  }

  export class Panel extends React.Component<PanelProps> {}
}

declare module "jointjs" {
  export namespace dia.Cell {
    interface Constructor<T extends Backbone.Model, P = {}> {
      new(opt?: { id: string } & P): T,
    }
  }
}
