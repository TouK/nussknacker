/* eslint-disable i18next/no-literal-string */
import { dia, V, Vectorizer } from "jointjs";
import { toXml } from "../../../common/SVGUtils";
import { exportStyled } from "./ExportStyled";
import { Theme } from "@mui/material";

function createStyle(theme: Theme) {
    const style = V("style").node;
    style.appendChild(document.createTextNode(exportStyled(theme).styles));
    return style;
}

function _debugInWindow(svg: string | SVGElement) {
    const svgString = typeof svg === "string" ? svg : toXml(svg);
    window.open(null).document.write(svgString);
}

function createDefInNeeded(image: Vectorizer, id: string) {
    const defs = image.defs();
    const existingDef = defs.findOne(`#${id}`);
    if (!existingDef) {
        const svg = document.getElementById(id)?.outerHTML;
        if (svg) {
            const def = V(svg, { id });
            defs.append(def);
        }
    }
}

function embedImages(svg: SVGElement) {
    const paper = V(svg);
    paper.find("use[*|href^='#']").forEach((image) => {
        const id = image.attr("xlink:href").replace(/^#/, "");
        createDefInNeeded(paper, id);
    });
}

function hasSize(el: SVGGraphicsElement) {
    const { width, height } = el.getBBox();
    return width || height;
}

function hasDisplay(el: Element) {
    return window.getComputedStyle(el).display !== "none";
}

const removeHiddenNodes = (root: SVGElement) =>
    Array
        // TODO: find better way
        .from(root.querySelectorAll<SVGGraphicsElement>("svg > g [style*='display'], svg > g [class], [noexport]"))
        .filter((el) => !hasSize(el) || !hasDisplay(el))
        .filter((el, i, all) => !all.includes(el.ownerSVGElement))
        .forEach((el) => el.remove());

const removeIncompatibles = (root: SVGElement) => {
    root.querySelectorAll<SVGGraphicsElement>(`[orient="auto-start-reverse"]`).forEach((el) => el.setAttribute("orient", "auto"));
};

function createPlaceholder(parent = document.body) {
    const el = document.createElement("div");
    el.style.position = "absolute";
    el.style.zIndex = "-1";
    el.style.left = "-10000px";
    el.style.visibility = "hidden";
    parent.append(el);
    return el;
}

function createPaper(placeholder: HTMLDivElement, maxSize: number, { options, defs }: Pick<dia.Paper, "options" | "defs">) {
    const paper = new dia.Paper({
        ...options,
        el: placeholder,
        width: maxSize,
        height: maxSize,
    });
    paper.defs.replaceWith(defs);
    paper.fitToContent({ allowNewOrigin: "any", padding: 10 });

    const { svg } = paper;
    const { width, height } = paper.getComputedSize();
    return { svg, width, height };
}

function addStyles(svg: SVGElement, height: number, width: number, theme: Theme) {
    svg.prepend(createStyle(theme));
    svg.setAttribute("height", height.toString());
    svg.setAttribute("width", width.toString());
    svg.setAttribute("class", "graph-export");
}

export async function prepareSvg(options: Pick<dia.Paper, "options" | "defs">, theme: Theme, maxSize = 15000) {
    const placeholder = createPlaceholder();
    const { svg, width, height } = createPaper(placeholder, maxSize, options);

    addStyles(svg, height, width, theme);
    removeHiddenNodes(svg);
    removeIncompatibles(svg);
    embedImages(svg);

    placeholder.remove();
    return toXml(svg);
}
