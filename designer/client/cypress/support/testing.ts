declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace Cypress {
        interface Chainable {
            addTestRecord: typeof addTestRecord;
        }
    }
}

const addTestRecord = (callback?: () => void) => {
    cy.get('[data-testid="data-grid-canvas"]').should("exist");
    cy.wait(500);
    cy.get('[data-testid="data-grid-canvas"]').then(($canvas) => {
        const rect = $canvas[0].getBoundingClientRect();
        // Click near bottom right corner
        const clickX = rect.width - 10;
        const clickY = rect.height - 10;

        cy.wrap($canvas).realClick({ x: clickX, y: clickY });
        if (callback) {
            callback();
        }
    });
};

Cypress.Commands.add("addTestRecord", addTestRecord);

export default {};
