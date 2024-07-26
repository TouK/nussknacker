describe("Compare versions", () => {
    const seed = "compareVersions";
    const fragmentSeed = "fragment";

    before(() => {
        cy.deleteAllTestProcesses({ filter: seed, force: true });
        cy.deleteAllTestProcesses({ filter: fragmentSeed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
        cy.deleteAllTestProcesses({ filter: fragmentSeed, force: true });
    });

    beforeEach(() => {
        cy.mockWindowDate();
        cy.viewport(1440, 1200);
    });

    it("should render compare a version with fragment input parameters change marked", () => {
        cy.createTestFragment(`${fragmentSeed}_xxx`, fragmentSeed);
        cy.visitNewProcess(`${seed}_yyy`, seed);

        const x = 800;
        const y = 800;

        // Add fragment to the scenario
        cy.contains(/^fragments$/)
            .should("exist")
            .scrollIntoView();
        cy.contains("fragment_xxx-test-process")
            .last()
            .should("be.visible")
            .move({ x, y, position: "right", force: true })
            .drag("#nk-graph-main", { x, y, position: "right", force: true });

        // Connect Existing node to the fragment
        cy.get(`[model-id="boundedSource"] circle`).trigger("mousedown");
        cy.get("#nk-graph-main")
            .trigger("mousemove", x, y, {
                clientX: x,
                clientY: y,
                moveThreshold: 5,
            })
            .trigger("mouseup", { force: true });

        // Change fragment param and save changes
        cy.get("[model-id^=e2e][model-id$=fragment_xxx-test-process]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").find('[title="i222"]').siblings().eq(0).find("#ace-editor").type("4");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();
        cy.get("[data-testid=window]").should("not.exist");

        // Change fragment param again and save changes
        cy.get("[model-id^=e2e][model-id$=fragment_xxx-test-process]").should("be.visible").trigger("dblclick");
        cy.get("[data-testid=window]").find('[title="i222"]').siblings().eq(0).find("#ace-editor").type("7");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();
        cy.contains(/^save\*$/i).click();
        cy.contains(/^ok$/i).click();

        // Open the compare dialog and select values to compare
        cy.contains(/^compare$/i).click();
        cy.get("[data-testid=window]").get("#otherVersion input").select(1);
        cy.get("[data-testid=window]").get("#differentVersion input").select(1);

        // Check current value for changed fragment parameter
        cy.get("[data-testid=window]")
            .contains(/Current version/i)
            .siblings()
            .eq(0)
            .find('[title="i222"]')
            .siblings()
            .eq(0)
            .find(".marked")
            .contains("47");

        // Check previous value for changed fragment parameter
        cy.get("[data-testid=window]")
            .contains(/Version 3/i)
            .siblings()
            .eq(0)
            .find('[title="i222"]')
            .siblings()
            .eq(0)
            .find(".marked")
            .contains("4");

        cy.get("[data-testid=window]").matchImage();
    });
});
