/* eslint-disable i18next/no-literal-string */
import * as joint from "jointjs"
import css from "!raw-loader!stylus-loader!./export.styl"
import {toXml} from "../../../common/SVGUtils"
import {memoize} from "lodash"

const xmlns = "http://www.w3.org/2000/svg"
let iconId = 1

function createStyle() {
  const style = document.createElementNS(xmlns, "style")
  style.appendChild(document.createTextNode(css))
  return style
}

function getCanvas(width: number, height: number) {
  const canvas = document.createElement("canvas")
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext("2d")
  return {canvas, ctx}
}

const getPng = memoize((url: string, width: number, height: number) => {
  const {canvas, ctx} = getCanvas(width, height)
  return new Promise<[string, string]>(resolve => {
    const id = `id${iconId++}`
    const img = new Image(width, height)
    img.onload = () => {
      ctx.drawImage(img, 0, 0)
      resolve([canvas.toDataURL("image/png"), id])
    }
    img.src = url
  })
})

function getPngScale(url: string, width: number, height: number, scale = 1) {
  return getPng(url, width * scale, height * scale)
}

export function debugInWindow(svgString: string) {
  window.open(null).document.write(svgString)
}

async function embedImage(image: SVGImageElement): Promise<[SVGImageElement, string]> {
  const {width, height, href} = image
  const [dataurl, id] = await getPngScale(href.baseVal, width.baseVal.value, height.baseVal.value, 10)
  image.setAttribute("xlink:href", dataurl)
  return [image, id]
}

function replaceWithDef(svg: SVGSVGElement) {
  const defs = document.createElementNS(xmlns, "defs")
  svg.prepend(defs)

  return ([image, id]: [SVGImageElement, string]) => {
    if (!svg.getElementById(id)) {
      const def = image.cloneNode() as SVGImageElement
      def.setAttribute("id", id)
      defs.append(def)
    }
    const use = document.createElementNS(xmlns, "use")
    use.setAttribute("xlink:href", `#${id}`)
    image.replaceWith(use)
  }
}

async function embedImages(svg: SVGSVGElement) {
  const nodes = svg.querySelectorAll<SVGImageElement>("image[*|href]")
  const externalImages = Array.from(nodes)
    .filter(({href}) => !href.baseVal.startsWith("data:image"))
    .map(embedImage)

  if (externalImages.length) {
    const embedded = await Promise.all(externalImages)
    embedded.forEach(replaceWithDef(svg))
  }
}

function hasSize(el: SVGGraphicsElement) {
  const {width, height} = el.getBBox()
  return width || height
}

function hasDisplay(el: Element) {
  return window.getComputedStyle(el).display !== "none"
}

const removeHiddenNodes = (root: SVGSVGElement) => Array
  // TODO: find better way
  .from(root.querySelectorAll<SVGGraphicsElement>("[style*='display'], [class]"))
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

function createPaper(placeholder: HTMLDivElement, maxSize: number, options: joint.dia.Paper.Options) {
  const paper = new joint.dia.Paper({
    ...options,
    el: placeholder,
    width: maxSize,
    height: maxSize,
  })

  paper.scaleContentToFit({minScale: 1.5, maxScale: 4})
  paper.fitToContent()

  const svg = paper.svg as SVGSVGElement
  const {width, height} = paper.getComputedSize()
  return {svg, width, height}
}

function addStyles(svg: SVGSVGElement, height: number, width: number) {
  svg.prepend(createStyle())
  svg.setAttribute("height", height.toString())
  svg.setAttribute("width", width.toString())
  svg.setAttribute("class", "graph-export")
}

export async function prepareSvg(options: joint.dia.Paper.Options, maxSize = 15000) {
  const placeholder = createPlaceholder()
  const {svg, width, height} = createPaper(placeholder, maxSize, options)

  addStyles(svg, height, width)
  removeHiddenNodes(svg)
  await embedImages(svg)

  placeholder.remove()
  return toXml(svg)
}
