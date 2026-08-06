const addCommentActivity = (comment: string) => {
    cy.intercept("/api/processes/*/*/activity/comment").as("comment");
    cy.contains("button", /add comment/i).click();
    cy.get("[data-testid=window]").find("textarea").eq(0).as("addCommentTextarea").click();
    cy.get("@addCommentTextarea").type(comment);
    cy.get("[data-testid=window]").find("button").contains(/^Add/i).click();
    cy.wait("@comment");
};

const addAttachmentActivity = (path: string) => {
    cy.intercept("/api/processes/*/*/activity/attachments").as("attachment");
    cy.contains(/add attachment/i).click();
    cy.get("[data-testid=window]").find("input").selectFile(path, { force: true });
    cy.get("[data-testid=window]").find("button").contains(/^Add/i).click();
    cy.wait("@attachment");
};

const findActivity = (query: string) => {
    cy.contains("Activities").scrollIntoView();
    cy.get('input[placeholder="type here to find past event"]').should("be.visible").clear().realType(query);
};

const makeScreenshot = () => {
    // FIXME: disabled snapshots because of styles blink problems (look for state & uuid)
    // cy.get('[data-testid="activities-panel"]').matchImage({
    //     maxDiffThreshold: 0.01,
    //     screenshotConfig: {
    //         blackout: [":has(>[data-testid='activity-date'])"],
    //     },
    // });
};

// TODO: disabled snapshots - testing almost nothing now
describe("Activities", () => {
    const seed = "activities";

    before(() => {
        cy.viewport("macbook-16");
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    after(() => {
        // force: a failure between deployScenario and cancelScenario leaves the scenario deployed;
        // archive/delete then respond with 409 and the leftover pollutes components usage counts in later specs
        cy.deleteAllTestProcesses({ filter: seed, force: true });
    });

    beforeEach(() => {
        cy.visitNewProcess(seed, "testProcess");
    });

    it("should display activities", () => {
        cy.contains(/creator panel/i).click();

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
        cy.contains("Activities").scrollIntoView();
        cy.contains(/^properties/i).click();
        cy.get("[data-testid=window]").find("input").eq(1).click().type("100");
        cy.contains(/^apply/i).click();

        cy.contains(/^save/i).click();
        cy.get("[data-testid=window]").find("textarea").click();
        cy.get("[data-testid=window]").find("textarea").type("test comment");
        cy.contains(/^ok/i).click();

        cy.get("[data-testid=compare-2]").eq(0).click();

        cy.contains("Version to compare").siblings().as("versionToCompare");
        cy.get("@versionToCompare").contains(/2 - created by admin/);
        cy.get("@versionToCompare").find("input").should("be.disabled");
        cy.get("#differentVersion input").select(1);
        cy.contains(/^ok/i).click();

        // Rename scenario activity
        cy.contains(/^properties/i).click();
        cy.get("[data-testid=window]").find("input").eq(0).as("nameInput").click();
        cy.get("@nameInput").type("-rename");
        cy.contains(/^apply/i).click();
        cy.contains(/^save/i).click();
        cy.contains(/^ok/i).click();
        cy.get("[data-testid=window]").should("not.exist");

        findActivity("comment 6");
        makeScreenshot();

        findActivity("comment 1");
        makeScreenshot();
        cy.contains(/^show less/i).click();
        makeScreenshot();

        addAttachmentActivity("cypress/fixtures/testProcess.json");
        findActivity("Attachment");
        makeScreenshot();

        // modify comment
        cy.intercept("/api/processes/*/activity/comment/*").as("editComment");
        cy.get("[data-testid=activity-row-3]").as("editCommentRow").realHover();
        cy.get("@editCommentRow").get("[data-testid=edit-comment-icon]").should("be.visible").click({ force: true });
        cy.get("[data-testid=window]").find("textarea").eq(0).type(" new comment");
        cy.get("[data-testid=window]").find("button").contains(/^Edit/i).realClick();
        cy.wait("@editComment");
        cy.get("[data-testid=activity-row-3]").contains("test comment new comment").should("be.visible");
    });

    it("should select and scroll to running version when running version in scenario actions click", () => {
        cy.window().then((win) => {
            const original = win.HTMLElement.prototype.scrollIntoView;

            // Cypress doesn't support scrollIntoView options in the same way as browsers do, smooth option is not supported.
            win.HTMLElement.prototype.scrollIntoView = function (options) {
                if (options && typeof options === "object") {
                    options.behavior = "auto";
                }
                original.call(this, options);
            };
        });

        cy.deployScenario();
        // the deployment activity is stored asynchronously after deploy; clicking "running version"
        // computes search results only once, so on slow CI it finds nothing when clicked too early
        cy.contains(/^Deployment$/).should("exist");
        cy.get('[data-testid="runningVersion"]').click();
        cy.get('[aria-selected="true"]').find('[aria-label="tool:Deployment"]').should("be.visible");
        cy.cancelScenario();
    });
});
