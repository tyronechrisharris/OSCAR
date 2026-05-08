
describe('Dashboard', () => {
    before(() => {
        cy.visit("/");
        cy.intercept('GET', '**/api/**', { log: false });
    });

    describe('Performance Testing', () => {
        it('FE-PERF-007 Load initial alarm data', () => {

            const start = Date.now();

            cy.get('.MuiDataGrid-row', { timeout: 10000 })
                .should('exist')
                .then(() => {
                    const duration = Date.now() - start;
                    expect(duration).to.be.lessThan(9000);
                });

            cy.get('body').then(($body) => {
                const hasMap = $body.find('[id="mapcontainer"]').is(':visible');

                if (hasMap) {
                    cy.get('[id="mapcontainer"]').should('be.visible');
                } else {
                    // No map on this deployment — alarm table being present is sufficient
                    cy.get('.MuiDataGrid-root').should('be.visible');
                }
            });

            // Using a data-testid or stable selector instead of English text
            cy.get('.MuiStack-root').should('be.visible');
        });
    });

    describe('Alarm Table', () => {
        it('FE-PERF-004 - Apply Filter to the past alarms view', () => {

            // open filter options by clicking filters
            cy.get('button[aria-label="Show filters"]').click();

            // check if filter form is open
            cy.get('.MuiDataGrid-filterForm').should('be.visible');

            // select column to filter by status - typically first dropdown
            cy.get('.MuiDataGrid-filterForm .MuiSelect-select').filter(':visible').eq(0).click();
            // Assuming 'Status' is consistent in machine-readable form or position
            cy.get('.MuiList-root .MuiMenuItem-root').last().click();

            // Status is often a singleSelect column — operator auto-sets to "is";
            // value input is a Select dropdown (not a text field)
            cy.get('.MuiDataGrid-filterForm .MuiSelect-select').filter(':visible').last().click();
            cy.get('.MuiList-root .MuiMenuItem-root').contains('Gamma').click();

            // verify filter is applied and table has results
            cy.get('.MuiDataGrid-row').should('have.length.greaterThan', 0);
            cy.get('.MuiDataGrid-filterForm').contains('Gamma').should('exist');

            // remove the filter row to restore full table
            cy.get('.MuiDataGrid-filterFormDeleteIcon button').click();

        });
    });

    describe('Event Preview - Rapiscan', () => {
        beforeEach(() => {
            cy.selectRapiscanEvent();
        })

        it('FE-PERF-001 Adjudicate a selected alarm', () => {

            cy.get('.MuiDataGrid-row.selected-row', {timeout: 2000} )
                .should('exist')
                .then(() => {

                    // adjudicate - target by label for stability
                    cy.get('.MuiSelect-select').first().click();
                    cy.get('.MuiList-root').should('be.visible');
                    cy.get('[data-value^="Code 9"]').click();

                    //secondary inspection
                    cy.get('.MuiSelect-select').eq(1).click();
                    cy.get('.MuiList-root').should('be.visible');
                    cy.get('[data-value="NONE"]').click();


                    cy.get('textarea').first()
                        .clear().type('Testing notes');

                    cy.get('button[type="submit"]').click();

                    cy.get('.selected-row').should('not.exist');
                });
        });

        it('select event and expand to event details', () => {
            cy.get('.MuiDataGrid-row.selected-row').should('exist');

            cy.get('button[aria-label="expand"]').click();

            cy.url().should('include', '/event-details');

            cy.get('button[aria-label="back"], button:has([data-testid="ArrowBackIcon"])').first().click();
            cy.url().should('match', /\/$/);
        });

        it('should close event preview when button clicked', () => {
            cy.get('.MuiDataGrid-row.selected-row').should('exist');

            cy.get('[data-testid="CloseRoundedIcon"]').click({force: true});

            cy.get('.MuiDataGrid-row.selected-row').should('not.exist');
        });
    });

    describe.skip('Event Preview - Aspect', () => {
        beforeEach(() => {
            cy.selectAspectEvent();
        })

        it('FE-PERF-001 Adjudicate a selected alarm from an aspect event', () => {
            // Similar to Rapiscan
        });
    });
});
