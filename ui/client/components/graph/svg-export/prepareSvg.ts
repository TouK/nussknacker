/* eslint-disable i18next/no-literal-string */
import css from "!raw-loader!./export.styl"
import * as joint from "jointjs"
import {memoize} from "lodash"
import {svgTowDataURL, toXml} from "../../../common/SVGUtils"

function createStyle() {
  const style = joint.V("style").node
  style.appendChild(document.createTextNode(css))
  return style
}

const getDataUrl = memoize(async (url: string) => {
  const response = await fetch(url)
  const svgStr = await response.text()
  return {dataurl: svgTowDataURL(svgStr), id: joint.util.uniqueId("img")}
})

function _debugInWindow(svg: string | SVGElement) {
  const svgString = typeof svg === "string" ? svg : toXml(svg)
  window.open(null).document.write(svgString)
}

async function embedImage(image: joint.Vectorizer) {
  const href = image.attr("xlink:href")
  const {dataurl, id} = await getDataUrl(href)
  image.attr("xlink:href", dataurl)
  return {dataurl, id}
}

function createDefInNeeded(image: joint.Vectorizer, id: string) {
  const defs = image.defs()
  const existingDef = defs.findOne(`#${id}`)
  if (!existingDef) {
    const def = joint.V(image.clone(), {id})
    defs.append(def)
    return def
  }
  return existingDef
}

async function replaceWithDef(img: joint.Vectorizer) {
  const {id} = await embedImage(img)
  const def = createDefInNeeded(img, id)
  const use = joint.V("use", {["xlink:href"]: `#${def.id}`})
  return img.node.replaceWith(use.node)
}

function embedImages(svg: SVGElement) {
  const images = joint.V(svg)
    .find("image[*|href]")
    .filter(i => !i.attr("xlink:href").startsWith("data:image"))

  return Promise.all(images.map(replaceWithDef))
}

function hasSize(el: SVGGraphicsElement) {
  const {width, height} = el.getBBox()
  return width || height
}

function hasDisplay(el: Element) {
  return window.getComputedStyle(el).display !== "none"
}

const removeHiddenNodes = (root: SVGElement) => Array
  // TODO: find better way
  .from(root.querySelectorAll<SVGGraphicsElement>("svg > g [style*='display'], svg > g [class]"))
  .filter(el => !hasSize(el) || !hasDisplay(el))
  .filter((el, i, all) => !all.includes(el.ownerSVGElement))
  .forEach(el => el.remove())

function createPlaceholder(parent = document.body) {
  const el = document.createElement("div")
  el.style.position = "absolute"
  el.style.zIndex = "-1"
  el.style.left = "-10000px"
  el.style.visibility = "hidden"
  parent.append(el)
  return el
}

function createPaper(placeholder: HTMLDivElement, maxSize: number, {options, defs}: Pick<joint.dia.Paper, "options" | "defs">) {
  const paper = new joint.dia.Paper({
    ...options,
    el: placeholder,
    width: maxSize,
    height: maxSize,
  })
  paper.defs.replaceWith(defs)
  paper.fitToContent({allowNewOrigin: "any"})

  const {svg} = paper
  const {width, height} = paper.getComputedSize()
  return {svg, width, height}
}

function addStyles(svg: SVGElement, height: number, width: number) {
  svg.prepend(createStyle())
  svg.setAttribute("height", height.toString())
  svg.setAttribute("width", width.toString())
  svg.setAttribute("class", "graph-export")
}

export async function prepareSvg(options: Pick<joint.dia.Paper, "options" | "defs">, a, maxSize = 15000) {
  const placeholder = createPlaceholder()
  const {svg, width, height} = createPaper(placeholder, maxSize, options)

  addStyles(svg, height, width)
  removeHiddenNodes(svg)
  await embedImages(svg)

  _debugInWindow(svg)
  placeholder.remove()
  return toXml(svg)
}

