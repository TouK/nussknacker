const width = 1440;
const height = 900;

describe("Creator toolbar", () => {
    const seed = "creator";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    beforeEach(() => {
        cy.viewport(width, height);
        cy.visitNewProcess(seed).as("processName");
        cy.contains(/^Creator panel.*sources/i).as("toolbar");
    });

    it("should allow filtering", () => {
        cy.get("@toolbar")
            .should("be.visible")
            .then((el) => {
                cy.matchImage({
                    // manual clip to fix wrong clipping of big invisible part
                    screenshotConfig: {
                        clip: {
                            x: el.offset().left,
                            y: el.offset().top,
                            width: el.width(),
                            height: Math.min(height, el.height()) - el.offset().top,
                        },
                    },
                });
            });
        cy.get("@toolbar").find("input").type("var");
        cy.get("@toolbar").matchImage();
    });
});
