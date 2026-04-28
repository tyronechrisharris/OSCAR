
describe('Servers Page (E2E)', () => {
    beforeEach(() => {
        // cy.visit('/servers');
        cy.visitServerPage();
    });

    describe('Adding Nodes', () => {

        it.skip('Should successfully add non-local node to the list of ndoes', () => {
            cy.get('input[name="name"]').clear().type('TEST Non-Local Node');
            cy.get('[name="name"]').should('have.value','TEST Non-Local Node');

            // Use environment variable for test node address
            const nodeAddress = Cypress.env('TEST_NODE_ADDRESS') || '127.0.0.1';
            cy.get('input[name="address"]').clear().type(nodeAddress);
            cy.get('[name="address"]').should('have.value', nodeAddress);

            // TODO: fix NaN when trying to update port reference
            // cy.get('input[name="port"]')
            //     .clear()
            //     .type('8282')
            //     .invoke('val')
            //     .then(Number)
            //     .should('equal', 8282);

            // cy.get('[name="port"]').should('have.value','8282');

            const username = Cypress.env('AUTH_USERNAME') || 'admin';
            const password = Cypress.env('AUTH_PASSWORD') || 'oscar';
            cy.get('input[name="username"]').clear().type(username);
            cy.get('[name="username"]').should('have.value', username);

            cy.get('input[name="password"]').clear().type(password);
            cy.get('[name="password"]').should('have.value', password);

            cy.contains('button','Add Node').click();
            cy.get('[id="saveNode-snackbar"]')
                .should('be.visible')
                .should('match',/Node is reachable | Node is not reachable. Try again./);

            cy.get('[id="saveNode-snackbar"]')
                .should('be.visible')
                .should('match',/OSCAR Configuration Saved | Failed to save OSCAR Configuration./);

        })
    });

    describe.skip('Editing/Removing Nodes', () => {
        it.skip('Edit existing node displays changes in node list', () => {

            cy.contains('button', 'Edit').first().click();

            cy.contains('Edit Node').should('be.visible');

            cy.get('input[name="name"]').clear().type('Testing Node');
            cy.get('input[name="address"]').clear().type('localhost');
            cy.get('input[name="port"]').clear().type('8282');
            const username = Cypress.env('AUTH_USERNAME') || 'admin';
            const password = Cypress.env('AUTH_PASSWORD') || 'oscar';
            cy.get('input[name="username"]').clear().type(username);
            cy.get('input[name="password"]').clear().type(password);


            cy.contains('button','Save Changes').click();

            cy.get('[id="saveNode-snackbar"]')
                .should('be.visible')
                .should('match',/Node is reachable | Node is not reachable. Try again./);


            cy.get('[id="saveNode-snackbar"]')
                .should('be.visible')
                .should('match',/OSCAR Configuration Saved | Failed to save OSCAR Configuration./);
        });

        it.skip('deleting node removes it from the list', () => {

        });

        it.skip('Cancel new node', () => {

            // fill out node form
            const username = Cypress.env('AUTH_USERNAME') || 'admin';
            const password = Cypress.env('AUTH_PASSWORD') || 'oscar';
            cy.get('input[name="name"]').clear().type('Testing Node');
            cy.get('input[name="address"]').clear().type('localhost');
            cy.get('input[name="port"]').clear().type('8282');
            cy.get('input[name="username"]').clear().type(username);
            cy.get('input[name="password"]').clear().type(password);


            cy.contains('button','Cancel').click();
        });
    });

});