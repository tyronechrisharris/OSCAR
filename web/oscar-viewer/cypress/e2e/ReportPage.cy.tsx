describe('Report Page (E2E)', () => {
    beforeEach(() => {
        cy.visitReportPage();
    });

    // Helper functions to reduce duplication
    const selectNode = () => {
        // Use label anchor to avoid matching the Navbar LanguageSelector
        cy.get('.MuiSelect-select').first().click();
        cy.get('.MuiList-root').should('be.visible');
        cy.get('.MuiList-root .MuiMenuItem-root', { timeout: 10000 })
            .should('have.length.greaterThan', 0)
            .first()
            .click();
    };

    const selectOption = (label: string, value: any, isMultiSelect: boolean) => {
        cy.contains('label', label)
            .parent()
            .find('.MuiSelect-select')
            .click();
        cy.get('.MuiList-root').should('be.visible');

        if (label === 'Lane Selector') {
            // Lane UIDs are server-generated; skip "Select All" and pick first real lane
            cy.get('.MuiList-root .MuiMenuItem-root').not('[data-value="all"]').first().click();
        } else {
            cy.get(`[data-value="${value}"]`).click();
        }

        if (isMultiSelect) {
            cy.get('body').type('{esc}');
        }
    };


    const generateReport = () => {
        cy.wait(200);
        cy.get('button[type="button"]').filter(':contains("Generate")').should('not.be.disabled').click();
    };

    const verifyReportGeneration = () => {
        cy.get('iframe', { timeout: 60000 })
            .should('exist')
            .and('be.visible')
            .then(($iframe) => {
                const src = $iframe.attr('src');
                expect(src, 'iframe has pdf src').to.match(/\.pdf$/);
                expect(src).to.not.be.empty;
            });
    };

    const generateAndVerifyReport = (config: any) => {
        selectNode();
        selectOption('Report Type', config.reportType, false);

        if (config.lane) {
            selectOption('Lane Selector', config.lane, true);
        }

        if (config.eventType) {
            selectOption('Event Type', config.eventType, false);
        }

        selectOption('Time Range', config.timeRange, false);

        generateReport();
        verifyReportGeneration();
    };

    describe('RDS Site Reports', () => {
        const timeRanges = [
            { value: 'last24Hrs', label: '24hrs' }
        ];

        timeRanges.forEach(({ value, label }) => {
            it(`should generate RDS Site report for ${label}`, () => {
                generateAndVerifyReport({
                    reportType: 'RDS_SITE',
                    timeRange: value
                });
            });
        });
    });

    describe('Lane Reports', () => {
        it(`should generate Lane report`, () => {
            generateAndVerifyReport({
                reportType: 'LANE',
                lane: 'first',
                timeRange: 'last24Hrs'
            });
        });
    });
});
