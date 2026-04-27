
describe('Event Log', () => {
    before(() => {
        cy.visitEventsPage();
        cy.intercept('GET', '**/api/**', { log: false });
    });

    describe('Performance Testing', () => {
        it('FE-PERF-007 Load initial data', () => {
            const start = Date.now();
            cy.get('.MuiDataGrid-row', {timeout: 10000})
                .should('exist')
                .then(() => {
                    const duration = Date.now() - start;
                    expect(duration).to.be.lessThan(3000);
                });
        });

        it('FE-PERF-005 - View details of a non-alarming occupancy.', () => {
            cy.selectNoneEvent();

            cy.get('.MuiDataGrid-row.selected-row', { timeout: 10000 })
                .find('button[aria-label="more"]')
                .should('exist')
                .click({force: true});


            cy.get('body .MuiMenuItem-root')
                .first() // Assuming Details is first
                .click({ force: true });

            cy.url().should('include', '/event-details');
            cy.get('button').filter(':has([data-testid="ArrowBackIcon"]), :contains("Back")').first().click();
            cy.url().should('include', '/event-log');
        });

        it('FE-PERF-004 - Apply Filter to the past alarms view', () => {

            // open filter options by clicking filters
            cy.get('button[aria-label="Show filters"]').click();

            // select column to filter by status
            cy.get('.MuiDataGrid-filterForm .MuiSelect-select').filter(':visible').eq(0).click();
            cy.get('.MuiList-root .MuiMenuItem-root').last().click();

            // Status is a singleSelect column — operator auto-sets to "is";
            // value input is a Select dropdown (not a text field)
            cy.get('.MuiDataGrid-filterForm .MuiSelect-select').filter(':visible').last().click();
            cy.get('.MuiList-root .MuiMenuItem-root').contains('Gamma').click();

            // verify filter is applied and table has results
            cy.get('.MuiDataGrid-row').should('have.length.greaterThan', 0);

            // remove the filter row to restore full table
            cy.get('.MuiDataGrid-filterFormDeleteIcon button').click();
        });
    });
});
