package io.github.mandala.sbdp.doma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DomaSqlTemplateParserTest {
    @Test
    void lexesControlBindLiteralAndEmbeddedDirectives() {
        String source = """
                select p.*
                  from projects p
                 where 1 = 1
                /*%if name != null */
                   and p.name = /* name */'sample'
                /*%end*/
                 order by /*# orderBy */
                """;

        DomaSqlTemplate template = new DomaSqlTemplateParser().parse(source);

        assertTrue(template.dynamic());
        assertEquals(4, template.directives().size());
        assertTrue(template.parameterExpressions().contains("name"));
        assertTrue(template.parameterExpressions().contains("orderBy"));
        assertFalse(template.parserReadySql().contains("sample"));
        assertTrue(template.parserReadySql().contains("p.name = ?"));
        assertTrue(template.parserReadySql().contains("mandala_dynamic_identifier"));
    }

    @Test
    void choosesOneRepresentativeConditionalBranchInsteadOfConcatenatingInvalidSql() {
        String source = """
                select * from projects
                /*%if active */ where deleted_at is null
                /*%else*/ where deleted_at is not null
                /*%end*/
                """;

        DomaSqlTemplate template = new DomaSqlTemplateParser().parse(source);

        assertTrue(template.parserReadySql().contains("where deleted_at is null"));
        assertFalse(template.parserReadySql().contains("is not null"));
        assertEquals(2, template.parserVariants().size());
        assertTrue(template.parserVariants().get(1).contains("where deleted_at is not null"));
    }

    @Test
    void preservesOrdinarySingleWordSqlComments() {
        DomaSqlTemplate template = new DomaSqlTemplateParser().parse("select * from projects /* active */ where id = 1");

        assertTrue(template.parserReadySql().contains("/* active */"));
        assertTrue(template.directives().isEmpty());
    }
}
