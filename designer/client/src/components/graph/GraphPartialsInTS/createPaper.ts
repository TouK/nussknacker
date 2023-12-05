import { dia } from "jointjs";
import { defaults } from "lodash";
import { defaultRouter } from "../EspNode/link";

export function createPaper(options: dia.Paper.Options): dia.Paper {
    return new dia.Paper({
        height: "100%",
        width: "100%",
        gridSize: 1,
        clickThreshold: 5,
        moveThreshold: 5,
        async: false,
        snapLinks: { radius: 30 },
        linkPinning: false,
        linkView: dia.LinkView.extend({
            options: defaults<dia.LinkView.Options, dia.LinkView.Options>(
                {
                    shortLinkLength: 60,
                    longLinkLength: 180,
                    linkToolsOffset: 20,
                    doubleLinkToolsOffset: 20,
                    doubleLinkTools: true,
                },
                dia.LinkView.prototype.options,
            ),
        }),
        defaultRouter,
        defaultConnector: {
            name: `rounded`,
            args: {
                radius: 60,
            },
        },
        ...options,
    });
}
