/* eslint-disable */
/**
 Copied from: https://github.com/sampumon/SVG.toDataURL

 The missing SVG.toDataURL library for your SVG elements.

 Licensed with the permissive MIT license, as follows.

 Copyright (c) 2010,2012 Sampu-mon & Mat-san

 Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

 */

class SVGUtils {

  toXml = (svg) => {
    // quick-n-serialize an SVG dom, needed for IE9 where there's no XMLSerializer nor SVG.xml
    // s: SVG dom, which is the <svg> elemennt
    function XMLSerializerForIE(s) {
      var out = "";

      out += "<" + s.nodeName;
      for (var n = 0; n < s.attributes.length; n++) {
        out += " " + s.attributes[n].name + "=" + "'" + s.attributes[n].value + "'";
      }

      if (s.hasChildNodes()) {
        out += ">\n";

        for (var n = 0; n < s.childNodes.length; n++) {
          out += XMLSerializerForIE(s.childNodes[n]);
        }

        out += "</" + s.nodeName + ">" + "\n";

      } else out += " />\n";

      return out;
    }


    if (window.XMLSerializer) {
      return (new XMLSerializer()).serializeToString(svg);
    } else {
      return XMLSerializerForIE(svg);
    }

  }

  svgToDataURL = svgStr => {
    const encoded = encodeURIComponent(svgStr)
      .replace(/'/g, '%27')
      .replace(/"/g, '%22')

    const header = 'data:image/svg+xml;charset=UTF-8,'
    const dataUrl = header + encoded

    return dataUrl
  }
}
//TODO this pattern is not necessary, just export every public function as in actions.js
export default new SVGUtils()
