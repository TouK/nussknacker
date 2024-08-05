/* eslint-disable i18next/no-literal-string */
import { attributes, dia, shapes } from "jointjs";
import { cloneDeepWith, isEmpty, toString } from "lodash";
import { NodeCounts, ProcessCounts } from "../../../reducers/graph";
import { NodeType, ProcessDefinitionData } from "../../../types";
import { getComponentIconSrc } from "../../toolbars/creator/ComponentIcon";
import { setLinksHovered } from "../utils/dragHelpers";
import { isConnected, isModelElement } from "../GraphPartialsInTS";
import { Events } from "../types";
import NodeUtils from "../NodeUtils";
import { EspNodeShape } from "./esp";
import millify from "millify";
import { UserSettings } from "../../../reducers/userSettings";
import { Theme } from "@mui/material";
import { blend } from "@mui/system";
import { blendLighten, getNodeBorderColor } from "../../../containers/theme/helpers";

const maxLineLength = 24;
const maxLineCount = 2;

function getBodyContent(bodyContent = ""): { text: string; multiline?: boolean } {
    if (bodyContent.length <= maxLineLength) {
        return {
            text: bodyContent,
        };
    }

    const splitContent = bodyContent.split(" ");

    if (splitContent[0].length > maxLineLength) {
        return {
            text: `${bodyContent.slice(0, maxLineLength)}...`,
        };
    }

    const tmpLines = [splitContent[0]];

    for (const str of splitContent.slice(1)) {
        const idx = tmpLines.length - 1;

        if (tmpLines[idx].length + str.length <= maxLineLength) {
            tmpLines[idx] += ` ${str}`;
            continue;
        }

        if (tmpLines.length >= maxLineCount) {
            tmpLines[idx] += "...";
            break;
        }

        if (str.length > maxLineLength) {
            tmpLines[idx + 1] = `${str.slice(0, maxLineLength)}...`;
            break;
        }

        tmpLines[idx + 1] = str;
    }

    const idx = tmpLines.length - 1;
    if (tmpLines[idx].length > maxLineLength) {
        tmpLines[idx] = `${tmpLines[idx].slice(0, maxLineLength)}...`;
    }

    return {
        text: tmpLines.join("\n"),
        multiline: tmpLines.length > 1,
    };
}

export function getStringWidth(str = "", pxPerChar = 8, padding = 7): number {
    return toString(str).length * pxPerChar + 2 * padding;
}

function getTestCounts(hasCounts: boolean, shortCounts: boolean, count: NodeCounts): string {
    if (!hasCounts) {
        return "";
    }

    if (shortCounts) {
        if (count && millify(count?.all)) {
            return count?.all?.toLocaleString();
        }
    } else if (count?.all?.toLocaleString()) {
        return count.all.toLocaleString() || "0";
    }

    return "?";
}

export const updateNodeCounts =
    (processCounts: ProcessCounts, userSettings: UserSettings, theme: Theme) =>
    (node: shapes.devs.Model): void => {
        const shortCounts = userSettings["node.shortCounts"];
        const count = processCounts[node.id];
        const hasCounts = !isEmpty(count);
        const hasErrors = hasCounts && count?.errors > 0;
        const testCounts = getTestCounts(hasCounts, shortCounts, count);
        const testResultsWidth = getStringWidth(testCounts);

        const testResultsSummary: attributes.SVGTextAttributes = {
            text: testCounts,
            fill: theme.palette.text.secondary,
            x: testResultsWidth / 2,
        };
        const testResults: attributes.SVGRectAttributes = {
            display: hasCounts ? "block" : "none",
            fill: hasErrors
                ? blend(blendLighten(theme.palette.background.paper, 0.04), theme.palette.error.main, 0.3)
                : blendLighten(theme.palette.background.paper, 0.04),
            stroke: hasErrors ? theme.palette.error.main : getNodeBorderColor(theme),
            strokeWidth: hasErrors ? 1 : 0.5,
            width: testResultsWidth,
        };
        node.attr({ testResultsSummary, testResults });
    };

export function makeElement(processDefinitionData: ProcessDefinitionData, theme: Theme): (node: NodeType) => shapes.devs.Model {
    return (node: NodeType) => {
        const description = node.additionalFields.description;
        const { text: bodyContent } = getBodyContent(node.id);
        const { text: helpContent } = getBodyContent(description ? "𝒊" : "");

        const iconHref = getComponentIconSrc(node, processDefinitionData);

        //This is used by jointjs to handle callbacks/changes
        //TODO: figure out what should be here?
        const definitionToCompare = {
            node: cloneDeepWith(node, (val, key: string) => {
                switch (key) {
                    case "additionalFields":
                    case "branchParameters":
                    case "parameters":
                        return null;
                }
            }),
            description,
        };

        const attributes: shapes.devs.ModelAttributes = {
            id: node.id,
            inPorts: NodeUtils.hasInputs(node) ? ["In"] : [],
            outPorts: NodeUtils.hasOutputs(node, processDefinitionData) ? ["Out"] : [],
            attrs: {
                background: {
                    fill: blendLighten(theme.palette.background.paper, 0.04),
                    opacity: node.isDisabled ? 0.5 : 1,
                },
                iconBackground: {
                    fill: theme.palette.custom.getNodeStyles(node).fill,
                    opacity: node.isDisabled ? 0.5 : 1,
                },
                icon: {
                    xlinkHref: iconHref,
                    opacity: node.isDisabled ? 0.5 : 1,
                },
                help: {
                    fontSize: theme.typography.h4.fontSize,
                    text: helpContent,
                },
                content: {
                    fontSize: theme.typography.body1.fontSize,
                    text: bodyContent,
                    opacity: node.isDisabled ? 0.3 : 1,
                    disabled: node.isDisabled,
                    fill: theme.palette.text.primary,
                },
                border: {
                    stroke: node.isDisabled ? "none" : getNodeBorderColor(theme),
                    strokeWidth: 1,
                },
            },
            rankDir: "R",
            nodeData: node,
            definitionToCompare,
        };

        const ThemedEspNodeShape = EspNodeShape(theme, node);
        const element = new ThemedEspNodeShape(attributes);

        element.once(Events.ADD, (e: dia.Element) => {
            // add event listeners after element setup
            setTimeout(() => {
                e.on(Events.CHANGE_POSITION, (el: dia.Element) => {
                    if (isModelElement(el) && !isConnected(el) && (el.hasPort("In") || el.hasPort("Out"))) {
                        setLinksHovered(el.graph, el.getBBox());
                    }
                });
            });
        });

        return element;
    };
}
