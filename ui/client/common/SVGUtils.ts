/**
 Copied from: https://github.com/sampumon/SVG.toDataURL

 The missing SVG.toDataURL library for your SVG elements.

 Licensed with the permissive MIT license, as follows.

 Copyright (c) 2010,2012 Sampu-mon & Mat-san

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 */

// quick-n-serialize an SVG dom, needed for IE9 where there's no XMLSerializer nor SVG.xml
// s: SVG dom, which is the <svg> elemennt
function xmlSerializerForIE(s): string {
  let out = ""

  out += `<${s.nodeName}`
  for (let n = 0; n < s.attributes.length; n++) {
    out += ` ${s.attributes[n].name}='${s.attributes[n].value}'`
  }

  if (s.hasChildNodes()) {
    out += ">\n"

    for (let n = 0; n < s.childNodes.length; n++) {
      out += xmlSerializerForIE(s.childNodes[n])
    }

    out += `</${s.nodeName}>`

  } else out += " />\n"

  return out
}

export function toXml(node: Node) {
  return window.XMLSerializer ? new XMLSerializer().serializeToString(node) : xmlSerializerForIE(node)
}

export function svgTowDataURL(svgStr: string) {
  return `data:image/svg+xml;base64,${btoa(unescape(encodeURIComponent(svgStr)))}`
}
