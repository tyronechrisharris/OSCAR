package org.sensorhub.impl.datastore.postgis.builder.filter;

import org.geotools.api.filter.Filter;
import org.geotools.api.filter.FilterFactory;
import org.geotools.factory.CommonFactoryFinder;
import org.junit.Test;
import static org.junit.Assert.*;

public class CQLFilterHandlerTest {

    private FilterFactory ff = CommonFactoryFinder.getFilterFactory();
    private CQLFilterHandler handler = new CQLFilterHandler();

    @Test
    public void testHandleNotEquals() {
        // Test with string
        Filter filter = ff.notEqual(ff.property("status"), ff.literal("Gamma"), false);
        String sql = handler.buildWhereClause(filter);
        assertTrue(sql.contains("jsonb_typeof(result->'status') = 'string'"));
        assertTrue(sql.contains("NOT (result @> '{\"status\": \"Gamma\"}'::jsonb)"));

        // Test with number
        filter = ff.notEqual(ff.property("value"), ff.literal(10), false);
        sql = handler.buildWhereClause(filter);
        assertTrue(sql.contains("jsonb_typeof(result->'value') = 'number'"));
        assertTrue(sql.contains("NOT (result @> '{\"value\": 10}'::jsonb)"));

        // Test with boolean
        filter = ff.notEqual(ff.property("active"), ff.literal(true), false);
        sql = handler.buildWhereClause(filter);
        assertTrue(sql.contains("jsonb_typeof(result->'active') = 'boolean'"));
        assertTrue(sql.contains("NOT (result @> '{\"active\": true}'::jsonb)"));

        // Test with null
        filter = ff.notEqual(ff.property("nullable"), ff.literal(null), false);
        sql = handler.buildWhereClause(filter);
        assertEquals("result->>'nullable' IS NOT NULL", sql);
    }

    @Test
    public void testNumericComparisons() {
        // Greater than
        Filter filter = ff.greater(ff.property("val"), ff.literal(5));
        String sql = handler.buildWhereClause(filter);
        assertEquals("(jsonb_typeof(result->'val') = 'number' AND (result->>'val')::numeric > 5)", sql);

        // Greater than or equal
        filter = ff.greaterOrEqual(ff.property("val"), ff.literal(5));
        sql = handler.buildWhereClause(filter);
        assertEquals("(jsonb_typeof(result->'val') = 'number' AND (result->>'val')::numeric >= 5)", sql);

        // Less than
        filter = ff.less(ff.property("val"), ff.literal(5));
        sql = handler.buildWhereClause(filter);
        assertEquals("(jsonb_typeof(result->'val') = 'number' AND (result->>'val')::numeric < 5)", sql);

        // Less than or equal
        filter = ff.lessOrEqual(ff.property("val"), ff.literal(5));
        sql = handler.buildWhereClause(filter);
        assertEquals("(jsonb_typeof(result->'val') = 'number' AND (result->>'val')::numeric <= 5)", sql);
    }

    @Test
    public void testNestedNumericComparison() {
        Filter filter = ff.greater(ff.property("foo.bar"), ff.literal(10));
        String sql = handler.buildWhereClause(filter);
        assertEquals("(jsonb_typeof(result->'foo'->'bar') = 'number' AND (result->'foo'->>'bar')::numeric > 10)", sql);
    }
}
