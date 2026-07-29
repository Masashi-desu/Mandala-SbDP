package io.github.mandala.sbdp.postgres;

/** Version-stable catalog queries targeting supported PostgreSQL releases (12+). */
public final class PostgresCatalogQueries {
    private PostgresCatalogQueries() {}

    public static final String DATABASE = """
            /* mandala:database */
            SELECT current_database() AS database_name,
                   current_setting('server_version') AS server_version
            """;

    public static final String SCHEMAS = """
            /* mandala:schemas */
            SELECT s.schema_name AS schema_name,
                   s.schema_owner AS owner_name,
                   obj_description(n.oid, 'pg_namespace') AS comment
              FROM information_schema.schemata s
              JOIN pg_catalog.pg_namespace n ON n.nspname = s.schema_name
             ORDER BY s.schema_name
            """;

    public static final String RELATIONS = """
            /* mandala:relations */
            SELECT n.nspname AS schema_name,
                   c.relname AS relation_name,
                   c.relkind::text AS relation_kind,
                   pg_get_userbyid(c.relowner) AS owner_name,
                   obj_description(c.oid, 'pg_class') AS comment,
                   CASE WHEN c.relkind IN ('v', 'm') THEN pg_get_viewdef(c.oid, true) ELSE NULL END AS definition,
                   pn.nspname AS parent_schema,
                   pc.relname AS parent_table,
                   c.relrowsecurity AS rls_enabled,
                   c.relforcerowsecurity AS rls_forced
              FROM pg_catalog.pg_class c
              JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
              LEFT JOIN pg_catalog.pg_inherits inh ON inh.inhrelid = c.oid
              LEFT JOIN pg_catalog.pg_class pc ON pc.oid = inh.inhparent
              LEFT JOIN pg_catalog.pg_namespace pn ON pn.oid = pc.relnamespace
             WHERE c.relkind IN ('r', 'p', 'f', 'v', 'm')
             ORDER BY n.nspname, c.relname
            """;

    public static final String COLUMNS = """
            /* mandala:columns */
            SELECT n.nspname AS schema_name,
                   c.relname AS relation_name,
                   a.attname AS column_name,
                   a.attnum AS ordinal,
                   pg_catalog.format_type(a.atttypid, a.atttypmod) AS formatted_type,
                   tn.nspname AS type_schema,
                   t.typname AS type_name,
                   NOT a.attnotnull AS nullable,
                   pg_get_expr(ad.adbin, ad.adrelid) AS default_expression,
                   a.attidentity::text AS identity_kind,
                   a.attgenerated::text AS generated_kind,
                   col_description(c.oid, a.attnum) AS comment
              FROM pg_catalog.pg_attribute a
              JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
              JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
              JOIN pg_catalog.pg_type t ON t.oid = a.atttypid
              JOIN pg_catalog.pg_namespace tn ON tn.oid = t.typnamespace
              LEFT JOIN pg_catalog.pg_attrdef ad ON ad.adrelid = a.attrelid AND ad.adnum = a.attnum
             WHERE a.attnum > 0
               AND NOT a.attisdropped
               AND c.relkind IN ('r', 'p', 'f', 'v', 'm')
             ORDER BY n.nspname, c.relname, a.attnum
            """;

    public static final String CONSTRAINTS = """
            /* mandala:constraints */
            SELECT n.nspname AS schema_name,
                   c.relname AS relation_name,
                   con.conname AS constraint_name,
                   con.contype::text AS constraint_type,
                   ARRAY(
                       SELECT a.attname
                         FROM unnest(con.conkey) WITH ORDINALITY AS key(attnum, ord)
                         JOIN pg_catalog.pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = key.attnum
                        ORDER BY key.ord
                   ) AS columns,
                   rn.nspname AS referenced_schema,
                   rc.relname AS referenced_table,
                   ARRAY(
                       SELECT a.attname
                         FROM unnest(con.confkey) WITH ORDINALITY AS key(attnum, ord)
                         JOIN pg_catalog.pg_attribute a ON a.attrelid = con.confrelid AND a.attnum = key.attnum
                        ORDER BY key.ord
                   ) AS referenced_columns,
                   pg_get_constraintdef(con.oid, true) AS definition,
                   con.condeferrable AS deferrable,
                   con.condeferred AS initially_deferred
              FROM pg_catalog.pg_constraint con
              JOIN pg_catalog.pg_class c ON c.oid = con.conrelid
              JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
              LEFT JOIN pg_catalog.pg_class rc ON rc.oid = con.confrelid
              LEFT JOIN pg_catalog.pg_namespace rn ON rn.oid = rc.relnamespace
             WHERE con.contype IN ('p', 'f', 'u', 'c', 'x')
             ORDER BY n.nspname, c.relname, con.conname
            """;

    public static final String INDEXES = """
            /* mandala:indexes */
            SELECT n.nspname AS schema_name,
                   c.relname AS relation_name,
                   ic.relname AS index_name,
                   i.indisunique AS is_unique,
                   i.indisprimary AS is_primary,
                   am.amname AS access_method,
                   ARRAY(
                       SELECT pg_get_indexdef(i.indexrelid, position, true)
                         FROM generate_series(1, i.indnkeyatts) AS position
                        ORDER BY position
                   ) AS columns,
                   pg_get_expr(i.indpred, i.indrelid) AS predicate,
                   pg_get_indexdef(i.indexrelid) AS definition
              FROM pg_catalog.pg_index i
              JOIN pg_catalog.pg_class c ON c.oid = i.indrelid
              JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
              JOIN pg_catalog.pg_class ic ON ic.oid = i.indexrelid
              JOIN pg_catalog.pg_am am ON am.oid = ic.relam
             ORDER BY n.nspname, c.relname, ic.relname
            """;

    public static final String SEQUENCES = """
            /* mandala:sequences */
            SELECT n.nspname AS schema_name,
                   c.relname AS sequence_name,
                   pg_catalog.format_type(s.seqtypid, NULL) AS data_type,
                   s.seqstart AS start_value,
                   s.seqmin AS minimum_value,
                   s.seqmax AS maximum_value,
                   s.seqincrement AS increment,
                   s.seqcache AS cache_size,
                   s.seqcycle AS cycle
              FROM pg_catalog.pg_sequence s
              JOIN pg_catalog.pg_class c ON c.oid = s.seqrelid
              JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
             ORDER BY n.nspname, c.relname
            """;

    public static final String ENUMS = """
            /* mandala:enums */
            SELECT n.nspname AS schema_name,
                   t.typname AS enum_name,
                   e.enumlabel AS enum_value,
                   obj_description(t.oid, 'pg_type') AS comment
              FROM pg_catalog.pg_type t
              JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
              JOIN pg_catalog.pg_enum e ON e.enumtypid = t.oid
             ORDER BY n.nspname, t.typname, e.enumsortorder
            """;

    public static final String DOMAINS = """
            /* mandala:domains */
            SELECT n.nspname AS schema_name,
                   t.typname AS domain_name,
                   pg_catalog.format_type(t.typbasetype, t.typtypmod) AS base_type,
                   t.typnotnull AS not_null,
                   t.typdefault AS default_expression,
                   ARRAY(
                       SELECT pg_get_constraintdef(con.oid, true)
                         FROM pg_catalog.pg_constraint con
                        WHERE con.contypid = t.oid
                        ORDER BY con.conname
                   ) AS checks,
                   obj_description(t.oid, 'pg_type') AS comment
              FROM pg_catalog.pg_type t
              JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
             WHERE t.typtype = 'd'
             ORDER BY n.nspname, t.typname
            """;

    public static final String FUNCTIONS = """
            /* mandala:functions */
            SELECT n.nspname AS schema_name,
                   p.proname AS function_name,
                   pg_get_function_identity_arguments(p.oid) AS identity_arguments,
                   pg_get_function_result(p.oid) AS result_type,
                   l.lanname AS language,
                   p.prokind::text AS function_kind,
                   p.provolatile::text AS volatility,
                   p.prosecdef AS security_definer,
                   pg_get_functiondef(p.oid) AS definition,
                   obj_description(p.oid, 'pg_proc') AS comment
              FROM pg_catalog.pg_proc p
              JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
              JOIN pg_catalog.pg_language l ON l.oid = p.prolang
             WHERE p.prokind IN ('f', 'p')
             ORDER BY n.nspname, p.proname, identity_arguments
            """;

    public static final String TRIGGERS = """
            /* mandala:triggers */
            SELECT n.nspname AS schema_name,
                   c.relname AS relation_name,
                   t.tgname AS trigger_name,
                   pg_get_triggerdef(t.oid, true) AS definition,
                   fn.nspname AS function_schema,
                   p.proname AS function_name,
                   t.tgenabled::text AS enabled
              FROM pg_catalog.pg_trigger t
              JOIN pg_catalog.pg_class c ON c.oid = t.tgrelid
              JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
              JOIN pg_catalog.pg_proc p ON p.oid = t.tgfoid
              JOIN pg_catalog.pg_namespace fn ON fn.oid = p.pronamespace
             WHERE NOT t.tgisinternal
             ORDER BY n.nspname, c.relname, t.tgname
            """;

    public static final String POLICIES = """
            /* mandala:policies */
            SELECT n.nspname AS schema_name,
                   c.relname AS relation_name,
                   p.polname AS policy_name,
                   p.polcmd::text AS command,
                   p.polpermissive AS permissive,
                   ARRAY(
                       SELECT CASE WHEN role_oid = 0 THEN 'PUBLIC' ELSE pg_get_userbyid(role_oid) END
                         FROM unnest(p.polroles) AS role_oid
                   ) AS roles,
                   pg_get_expr(p.polqual, p.polrelid) AS using_expression,
                   pg_get_expr(p.polwithcheck, p.polrelid) AS check_expression
              FROM pg_catalog.pg_policy p
              JOIN pg_catalog.pg_class c ON c.oid = p.polrelid
              JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
             ORDER BY n.nspname, c.relname, p.polname
            """;
}
