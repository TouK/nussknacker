/* eslint-disable i18next/no-literal-string */
import { dia, routers } from "jointjs";
import { Edge, EdgeKind, ProcessDefinitionData, ScenarioGraph } from "../../../types";
import NodeUtils from "../NodeUtils";

const LINK_TEXT_COLOR = "#686868";
const LINK_COLOR = "#F5F5F5";

function makeLabels(label = "", prefix = ""): dia.Link.Label[] {
    const havePrefix = prefix.length > 0;
    return label.length === 0
        ? []
        : [
              {
                  position: 0.5,
                  attrs: {
                      rect: {
                          ref: "text",
                          refX: -6,
                          refY: -6,
                          refWidth: "100%",
                          refHeight: "100%",
                          refWidth2: 12,
                          refHeight2: 12,
                          stroke: LINK_TEXT_COLOR,
                          fill: LINK_COLOR,
                          strokeWidth: 1,
                          rx: 5,
                          ry: 5,
                          cursor: "pointer",
                      },
                      text: {
                          text: havePrefix ? `${prefix}: ${label}` : label,
                          fontWeight: 600,
                          fontSize: 10,
                          fill: LINK_TEXT_COLOR,
                      },
                  },
              },
          ];
}

export function makeLink(edge: Edge & { index?: number }, paper: dia.Paper) {
    const edgeLabel = NodeUtils.edgeLabel(edge);
    const switchEdges: string[] = [EdgeKind.switchNext, EdgeKind.switchDefault];
    const labels = makeLabels(edgeLabel, switchEdges.includes(edge.edgeType?.type) ? `${edge.index}` : "");
    return (
        paper
            .getDefaultLink(null, null)
            //TODO: some different way to create id? Must be deterministic and unique
            .prop("id", edgeLabel ? `${edge.from}-${edge.to}-${edgeLabel}` : `${edge.from}-${edge.to}`)
            .source({
                id: edge.from,
                port: "Out",
                anchor: { name: "bottom", args: { dy: -4 } },
                connectionPoint: { name: "boundary", args: { offset: 2 } },
            })
            .target({
                id: edge.to,
                port: "In",
                anchor: { name: "top", args: { dy: 4 } },
                connectionPoint: { name: "boundary", args: { offset: 5 } },
            })
            .labels(labels)
            .prop("edgeData", edge)
            .prop("definitionToCompare", { edge })
    );
}

const startDirections: dia.OrthogonalDirection[] = ["bottom"];
const endDirections: dia.OrthogonalDirection[] = ["top"];
export const defaultRouter: routers.RouterJSON = {
    name: `manhattan`,
    args: {
        startDirections,
        endDirections,
        step: 15,
        padding: 20,
        maximumLoops: 200,
    },
};

export function getDefaultLinkCreator(arrowMarkerId: string, scenarioGraph: ScenarioGraph, processDefinition: ProcessDefinitionData) {
    return (cellView: dia.CellView, magnet: SVGElement) => {
        const isReversed = magnet?.getAttribute("port") === "In";

        const link = new dia.Link({
            markup: '<path class="connection"/><path class="connection-wrap"/><g class="marker-vertices"/><g class="marker-arrowheads"/><g class="link-tools"/>',
            attrs: {
                ".connection": {
                    markerStart: isReversed ? `url(#${arrowMarkerId})` : null,
                    markerEnd: isReversed ? null : `url(#${arrowMarkerId})`,
                },
                ".link-tools": {
                    noExport: true,
                },
            },
        });

        link.router({
            ...defaultRouter,
            args: {
                ...defaultRouter.args,
                startDirections: isReversed ? endDirections : startDirections,
                endDirections: isReversed ? startDirections : endDirections,
            },
        });

        link.on("change:target", (link) => {
            const model = cellView?.model;
            const sourceId = isReversed ? link.target()?.id : model?.id;
            const targetId = isReversed ? model?.id : link.target()?.id;
            if (sourceId) {
                const edge = NodeUtils.getEdgeForConnection({
                    fromNode: NodeUtils.getNodeById(sourceId, scenarioGraph),
                    toNode: NodeUtils.getNodeById(targetId, scenarioGraph),
                    processDefinition,
                    scenarioGraph,
                });
                link.labels(makeLabels(NodeUtils.edgeLabel(edge)));
            } else {
                link.labels([]);
            }
        });

        return link;
    };
}
