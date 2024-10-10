const addCommentActivity = (comment: string) => {
    cy.intercept("/api/processes/*/*/activity/comment").as("comment");
    cy.contains(/add comment/i).click();
    cy.get("[data-testid=window]").should("be.visible").find("textarea").eq(0).click().type(comment);
    cy.get("[data-testid=window]").find("button").contains(/^Add/i).click();
    cy.wait("@comment");
};

const addAttachmentActivity = (path: string) => {
    cy.intercept("/api/processes/*/*/activity/attachments").as("attachment");
    cy.contains(/add attachment/i).click();
    cy.get("[data-testid=window]").should("be.visible").find("input").selectFile(path, { force: true });
    cy.get("[data-testid=window]").find("button").contains(/^Add/i).click();
    cy.wait("@attachment");
};

const findActivity = (query: string) => {
    cy.contains("Activities").should("exist").scrollIntoView();
    cy.get('input[placeholder="type here to find past event"]').clear().type(query);
};

const makeScreenshot = () => {
    cy.get('[data-testid="activities-panel"]').matchImage({ maxDiffThreshold: 0.01 });
};

describe("Activities", () => {
    const seed = "activities";

    before(() => {
        cy.viewport("macbook-16");
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        cy.deleteAllTestProcesses({ filter: seed });
    });

    beforeEach(() => {
        cy.visitNewProcess(seed, "testProcess");
    });

    it("should display activities", () => {
        cy.getTestProcessName(seed, "001").then((name) => {
            cy.archiveProcess(name);
            cy.unarchiveProcess(name);
            cy.migrateProcess(name, 2);
        });

        addCommentActivity("comment 1");
        addCommentActivity("comment 2");
        addCommentActivity("comment 3");
        addCommentActivity("comment 4");
        addCommentActivity("comment 5");
        addCommentActivity("comment 6");

        // Compare action
        cy.contains("Activities").should("exist").scrollIntoView();
        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").should("be.visible").find("input").eq(1).click().type("100");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();

        cy.contains(/^save/i).should("be.enabled").click();
        cy.get("[data-testid=window]").should("be.visible").find("textarea").click().type("test comment");
        cy.contains(/^ok/i).should("be.enabled").click();

        cy.get("[data-testid=compare-2]").eq(0).click();

        cy.contains("Version to compare").siblings().as("versionToCompare");
        cy.get("@versionToCompare").contains(/2 - created by admin/);
        cy.get("@versionToCompare").find("input").should("be.disabled");
        cy.contains("Difference to pick").get("#differentVersion input").select(1);
        cy.contains(/^ok/i).should("be.enabled").click();

        // Rename scenario activity
        cy.contains(/^properties/i)
            .should("be.enabled")
            .click();
        cy.get("[data-testid=window]").should("be.visible").find("input").eq(0).click().type("-rename");
        cy.contains(/^apply/i)
            .should("be.enabled")
            .click();
        cy.contains(/^save/i).should("be.enabled").click();
        cy.contains(/^ok/i).should("be.enabled").click();

        findActivity("comment 6");
        makeScreenshot();

        cy.contains(/^show 5 more/i).click();

        findActivity("comment 1");
        makeScreenshot();
        cy.contains(/^show less/i).click();
        makeScreenshot();

        addAttachmentActivity("cypress/fixtures/testProcess.json");
        findActivity("Attachment");
        makeScreenshot();
    });
});
