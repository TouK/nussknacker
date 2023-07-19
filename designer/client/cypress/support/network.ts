declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace Cypress {
        interface Chainable {
            goOffline: typeof goOffline;
            goOnline: typeof goOnline;
        }
    }
}

function goOffline() {
    cy.log("**go offline**")
        // stub every request with a StaticResponse to simulate network error
        .then(() => cy.intercept("*", { forceNetworkError: true }))
        .then(() =>
            cy.window().then((win) => {
                cy.stub(win.navigator, "onLine").value(false);
                cy.wrap(win).trigger("offline");
            }),
        );
}
function goOnline() {
    cy.log("**go online**")
        // go back to normal network behavior
        .then(() =>
            cy.intercept("GET", "*", (req) => {
                req.continue();
            }),
        )
        .then(() =>
            cy.window().then((win) => {
                cy.stub(win.navigator, "onLine").value(true);
                cy.wrap(win).trigger("online");
            }),
        );
}

Cypress.Commands.add("goOnline", goOnline);
Cypress.Commands.add("goOffline", goOffline);

export default {};
