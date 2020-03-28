/* eslint-disable i18next/no-literal-string */
import "@babel/polyfill"
import dagre, {graphlib} from "dagre"
import * as joint from "jointjs"

// import {JSDOM} from "jsdom"

// const dom = new JSDOM("<!DOCTYPE html><div></div>")

export async function layout(json?: any) {
  const graph = new joint.dia.Graph()
  graph.fromJSON(json)
  const cells = graph.getCells().filter(cell => !cell.get("backgroundObject"))

  joint.layout.DirectedGraph.layout(cells, {
    graphlib,
    dagre,
    nodeSep: 0,
    edgeSep: 0,
    rankSep: 75,
    rankDir: "TB",
  })

  // const layout = cells
  //   .filter(el => !el.get("backgroundObject"))
  //   .map(el => ({id: el.id, position: el.get("position")}))

  return graph.toJSON()
}
