describe("Connection error", () => {
    beforeEach(() => {
        cy.viewport(1400, 1000);
        cy.visit("/");
    });

    afterEach(() => {
        cy.goOnline();
    });

    it("should display connection errors", () => {
        const verifyNoNetworkAccess = () => {
            cy.log("verify no network access");
            cy.intercept("/api/notifications").as("notifications");

            cy.wait("@notifications");
            cy.goOffline();
            cy.contains(/No network access/).should("be.visible");
            cy.get("body").matchImage();
            cy.goOnline();
            cy.contains(/No network access/).should("not.exist");
        };

        const verifyNoBackendAccess = () => {
            cy.log("verify no backend access");

            cy.intercept("/api/notifications", { statusCode: 502 });

            cy.contains(/Backend connection issue/).should("be.visible");
            cy.get("body").matchImage();

            cy.intercept("/api/notifications", (req) => {
                req.continue();
            });

            cy.contains(/Backend connection issue/).should("not.exist");
        };

        verifyNoNetworkAccess();
        verifyNoBackendAccess();
    });
});
