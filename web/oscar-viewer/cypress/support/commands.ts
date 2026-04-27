/// <reference types="cypress" />

// Automatically inject basic auth credentials on every visit if provided via environment
Cypress.Commands.overwrite('visit', (originalFn, url, options: any = {}) => {
    const auth = Cypress.env('AUTH_USERNAME') && Cypress.env('AUTH_PASSWORD')
        ? { username: Cypress.env('AUTH_USERNAME'), password: Cypress.env('AUTH_PASSWORD') }
        : undefined;

    return originalFn(url, {
        auth,
        ...options,
    });
});


Cypress.Commands.add('selectRapiscanEvent', () => {
    // Deselect first if a row is already selected (to avoid toggling off),
    // then select the first row fresh and wait for the EventPreview to open.
    cy.get('.MuiDataGrid-row').first().then(($row) => {
        if ($row.hasClass('selected-row')) {
            cy.get('.MuiDataGrid-row').first().click(); // deselect
        }
    });
    cy.get('.MuiDataGrid-row').first().click();
    // Use ARIA label or data-testid instead of English text when possible
    // For now, using what is available in the UI.
    cy.get('[data-field="occupancyId"]', { timeout: 8000 }).should('exist');
});

Cypress.Commands.add('selectNoneEvent', () => {
    cy.get('.MuiDataGrid-row').then(($rows) => {
        const selectedRow = $rows.filter('.selected-row');
        if (selectedRow.length === 0) {
            cy.get('.MuiDataGrid-row')
                .first()
                .click();
        }
    });
});

Cypress.Commands.add('selectAspectEvent', () => {
    cy.get('.MuiDataGrid-row').then(($rows) => {
        const selectedRow = $rows.filter('.selected-row');
        if (selectedRow.length === 0) {
            cy.get('.MuiDataGrid-row')
                .first()
                .click();
        }
    });
});

Cypress.Commands.add('selectEventAndExpandDetails', () => {
    cy.selectRapiscanEvent();

    cy.get('button[aria-label="expand"]').click();

    cy.url().should('include', '/event-details');

    // Locale stable check
    cy.get('.MuiTypography-root').should('exist');
});

// Navigation commands use direct URL visits — MUI icon data-testid attributes are
// stripped in production Next.js builds, making icon-click navigation unreliable.

Cypress.Commands.add('visitDashboardPage', () => {
    cy.visit('/');
    cy.url().should('match', /\/$/);
});

Cypress.Commands.add('visitNationalPage', () => {
    cy.visit('/national-view');
    cy.url().should('include', '/national-view');
});

Cypress.Commands.add('visitMapPage', () => {
    cy.visit('/map');
    cy.url().should('include', '/map');
});

Cypress.Commands.add('visitEventsPage', () => {
    cy.visit('/event-log');
    cy.url().should('include', '/event-log');
});

Cypress.Commands.add('visitServerPage', () => {
    cy.visit('/servers');
    cy.url().should('include', '/servers');
});

Cypress.Commands.add('visitLaneViewPage', () => {
    cy.visit('/');
    // Click the first available lane status item in the Lane Status section
    cy.get('[data-testid^="lane-status-"]')
        .first()
        .should('be.visible')
        .click();

    cy.url().should('include', '/lane-view');
});

Cypress.Commands.add('visitReportPage', () => {
    cy.visit('/report');
    cy.url().should('include', '/report');
});
