import nodeMarkup from "../markups/node.html"
import {attrsConfig, portInAttrs, portOutAttrs} from "./attrs"
import * as joint from "jointjs/index"

export const EspNodeShape = joint.shapes.devs.Model.extend({
  markup: nodeMarkup,
  portMarkup: "<g class=\"port\"><circle class=\"port-body\"/></g>",
  portLabelMarkup: null,

  defaults: joint.util.deepSupplement({
    type: "devs.GenericModel",
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
  }, joint.shapes.devs.Model.prototype.defaults),
})
