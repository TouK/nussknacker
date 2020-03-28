import * as joint from "jointjs"
import {set} from "lodash"
import nodeMarkup from "../markups/node.html"
import {attrsConfig, portInAttrs, portOutAttrs} from "./attrs"

export const EspNodeShape = joint.shapes.devs.Model.define(
  "custom.GenericModel",
  joint.util.defaultsDeep(
    {
      attrs: attrsConfig(),
      size: {width: 1, height: 1},
      inPorts: [],
      outPorts: [],
      ports: {
        groups: {
          in: {
            position: "top",
            attrs: portInAttrs(),
          },
          out: {
            position: "bottom",
            attrs: portOutAttrs(),
          },
        },
      },
    },
    joint.shapes.devs.Model.prototype.defaults,
  ),
  {
    markup: nodeMarkup,
    portMarkup: "<g class=\"port\"><circle class=\"port-body\"/></g>",
    portLabelMarkup: null,
  },
)

set(joint.shapes, "custom.GenericModel", EspNodeShape)
