import { defaultsDeep } from "lodash";
import UAParser from "ua-parser-js";
import { recurse } from "cypress-recurse";
import JQueryWithSelector = Cypress.JQueryWithSelector;

declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace Cypress {
        interface TriggerOptions {
            moveThreshold?: number;
        }

        interface MatchImageOptionsExtended extends Cypress.MatchImageOptions {
            updateSnapshotsOnFail?: boolean;
        }

        interface Chainable {
            matchImage(options?: Cypress.MatchImageOptionsExtended): Chainable<MatchImageReturn>;
        }
    }
}

type RequestArgs =
    | [string]
    | [string, Cypress.RequestBody]
    | [Cypress.HttpMethod, string]
    | [Cypress.HttpMethod, string, Cypress.RequestBody]
    | [Partial<Cypress.RequestOptions>];

const getRequestOptions = (...[first, second, third]: RequestArgs): Partial<Cypress.RequestOptions> => {
    return typeof first === "string"
        ? typeof second === "string"
            ? { method: first, url: second, body: third }
            : { url: first, body: second }
        : first;
};

Cypress.Commands.overwrite("request", (original, ...args: RequestArgs) =>
    original({
        auth: {
            username: Cypress.env("testUserUsername"),
            password: Cypress.env("testUserPassword"),
        },
        ...getRequestOptions(...args),
    }),
);

type VisitArgs =
    | [string]
    | [string, Partial<Cypress.VisitOptions>]
    | [
          Partial<Cypress.VisitOptions> & {
              url: string;
          },
      ];
Cypress.Commands.overwrite("visit", (original, ...args: VisitArgs) => {
    const [first, second] = args;
    const auth = {
        username: Cypress.env("testUserUsername"),
        password: Cypress.env("testUserPassword"),
    };

    const { name: os } = new UAParser().getOS();
    const pixelRatio = window.devicePixelRatio;
    const osDir = pixelRatio !== 1 ? `${os}/x${pixelRatio}` : os;
    Cypress.env(
        defaultsDeep(
            {
                pluginVisualRegressionImagesPath: `{spec_path}/__image_snapshots__/${Cypress.browser.name}/${osDir}`,
            },
            Cypress.env(),
        ),
    );
    return original(typeof first === "string" ? { auth, ...second, url: first } : { auth, ...first });
});

const originalCaretColorName = "data-original-caret-color";
const originalCaretDisplayName = "data-original-caret-display";

const hideInputCaret = ($el: JQueryWithSelector) => {
    cy.get("input, textarea, select")
        .should("have.length.gte", 0)
        .each(($input) => {
            const originalCaretColor = $el.css("caret-color");
            $el.attr(originalCaretColorName, originalCaretColor);
            $input.css("caret-color", "transparent");
        });

    cy.get(".ace_cursor")
        .should("have.length.gte", 0)
        .each(($input) => {
            const originalCaretDisplay = $el.css("display");
            $el.attr(originalCaretDisplayName, originalCaretDisplay);
            $input.css("display", "none");
        });
};

const showInputCaret = ($el: JQueryWithSelector) => {
    cy.get("input, textarea, select")
        .should("have.length.gte", 0)
        .each(($input) => {
            const originalCaretColor = $el.attr(originalCaretColorName);
            $input.css("caret-color", originalCaretColor);
        });

    cy.get(".ace_cursor")
        .should("have.length.gte", 0)
        .each(($input) => {
            const originalCaretDisplay = $el.attr(originalCaretDisplayName);
            $input.css("display", originalCaretDisplay);
        });
};

Cypress.Commands.overwrite<"matchImage", "element">(
    "matchImage",
    (originalFn, $el, { updateSnapshotsOnFail, ...options }: Cypress.MatchImageOptionsExtended = {}) => {
        hideInputCaret($el);

        cy.wait(200);

        if (updateSnapshotsOnFail || Cypress.env("updateSnapshotsOnFail")) {
            let path = null;
            const threshold = options?.maxDiffThreshold || Cypress.env("pluginVisualRegressionMaxDiffThreshold");
            return recurse(
                () =>
                    originalFn($el, {
                        ...options,
                        maxDiffThreshold: 1,
                        matchAgainstPath: path,
                        updateImages: !!path,
                        title: path && "__temp", //prevent # mismatch
                    }),
                (r) => r.diffValue < threshold,
                {
                    log: false,
                    delay: 200,
                    limit: 2,
                    yield: "value",
                    postLastValue: true,
                    post: ({ value, limit, success }) => {
                        path = path || value.imgPath;
                        if (!success) {
                            return cy.log("Snapshot needs update", value);
                        }
                        if (limit <= 1) {
                            return cy.log("Updated snapshot", value);
                        }
                    },
                },
            );
        }
        originalFn($el, options);
        cy.wait(200);
        showInputCaret($el);
    },
);

Cypress.Commands.overwrite<"trigger", "element">("trigger", (originalFn, subject, eventName, ...args) => {
    // Number of required mousemove events before the first pointermove event will be triggered. (for jointjs)
    const options = args[2];
    const moveThreshold = options?.moveThreshold;
    if (["mousemove", "pointermove", "touchmove"].includes(eventName) && moveThreshold) {
        for (let i = 0; i < moveThreshold; i++) {
            // eslint-disable-next-line @typescript-eslint/ban-ts-comment
            // @ts-ignore
            originalFn(subject, eventName, options);
        }
    }
    return originalFn(subject, eventName, ...args);
});

Cypress.Commands.overwrite<"select", "element">("select", (originalFn, subject, value, options) => {
    const handleReactSelectSelection =
        subject[0].className.includes("singleValue") || subject[0].className.includes("control") || subject[0].ariaLabel === "type-select";

    if (handleReactSelectSelection) {
        cy.wrap(subject).click({ force: true });
        cy.get(`[id$='option-${value}']`).click({ force: true });
    } else {
        return originalFn(subject, value, options);
    }
});
