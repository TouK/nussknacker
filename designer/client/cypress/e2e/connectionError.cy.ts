const screenshotConfig = {
    blackout: ["[data-testid='SidePanel']"],
};

describe("Connection error", () => {
    const NAME = "no-backend";

    before(() => {
        cy.deleteAllTestProcesses({ filter: NAME, force: true });
    });

    beforeEach(() => {
        cy.viewport(1400, 1000);
    });

    afterEach(() => {
        cy.goOnline();
    });

    it("should display connection errors", () => {
        cy.visit("/");

        const verifyNoNetworkAccess = () => {
            cy.log("verify no network access");
            cy.intercept("/api/notifications").as("notifications");

            cy.wait("@notifications");
            cy.goOffline();
            cy.contains(/No network access/).should("be.visible");
            cy.get("body").matchImage({
                screenshotConfig,
            });
            cy.goOnline();
            cy.contains(/No network access/).should("not.exist");
        };

        const verifyNoBackendAccess = () => {
            cy.log("verify no backend access");

            cy.intercept("/api/notifications", { statusCode: 502 });

            cy.contains(/Backend connection issue/).should("be.visible");
            cy.get("body").matchImage({
                screenshotConfig,
            });

            cy.intercept("/api/notifications", (req) => {
                req.continue();
            });

            cy.contains(/Backend connection issue/).should("not.exist");
        };

        const verifyNoBackendAccessWhenScenarioEditNodeModalOpens = () => {
            cy.log("verify no backend access when scenario edit modal opens");
            cy.visitNewProcess(NAME, "filter");

            cy.intercept("POST", "/api/nodes/*/validation").as("validation");
            cy.get("[model-id='filter']").dblclick();
            cy.wait("@validation");

            cy.intercept("/api/notifications/*", { statusCode: 502 });
            cy.contains(/Backend connection issue/).should("be.visible");
            cy.get("body").matchImage({
                screenshotConfig,
            });

            cy.intercept("/api/notifications", (req) => {
                req.continue();
            });

            cy.contains(/Backend connection issue/).should("not.exist");
        };

        verifyNoNetworkAccess();
        verifyNoBackendAccess();
        verifyNoBackendAccessWhenScenarioEditNodeModalOpens();
    });

    it("should cancel request when connection error", () => {
        cy.clock();

        const statusIntervalTick = 10000;
        const visibleStatusToastMessageBeforeConnectionError = () => {
            cy.intercept("/api/processes/*/status", { statusCode: 502 });

            // Check if the status toast message is not visible after the backend connection issue. We need to speed up interval to not wait 12s for a request
            cy.contains(/Cannot fetch status/).should("be.visible");

            cy.intercept("/api/processes/*/status", (req) => {
                req.continue();
            });
        };
        const notVisibleStatusToastMessageWhenConnectionError = () => {
            cy.contains(/Cannot fetch status/).should("not.exist");
        };

        cy.visitNewProcess(NAME, "filter");

        cy.contains("svg", /filter/i).dblclick();

        cy.tick(statusIntervalTick * 3);

        visibleStatusToastMessageBeforeConnectionError();

        cy.tick(1000);

        cy.intercept("/api/notifications", { statusCode: 502 });

        cy.tick(statusIntervalTick * 3);

        notVisibleStatusToastMessageWhenConnectionError();

        cy.intercept("/api/notifications", (req) => {
            req.continue();
        });

        cy.clock().invoke("restore");
    });
});
