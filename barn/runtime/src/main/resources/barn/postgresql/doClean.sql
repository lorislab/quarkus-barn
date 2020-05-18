-- drop all materialized views
DO $$ DECLARE
    r RECORD;
BEGIN
    FOR r IN (SELECT relname FROM pg_catalog.pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE c.relkind = 'm' AND n.nspname = current_schema()) LOOP
            EXECUTE 'DROP MATERIALIZED VIEW IF EXISTS ' || quote_ident(r.relname) || ' CASCADE';
        END LOOP;

-- drop all statement views
    FOR r IN (SELECT relname FROM pg_catalog.pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                              LEFT JOIN pg_depend dep ON dep.objid = c.oid AND dep.deptype = 'e'
                              WHERE c.relkind = 'v' AND  n.nspname = current_schema() AND  dep.objid IS NULL) LOOP
            EXECUTE 'DROP VIEW IF EXISTS ' || quote_ident(r.relname) || ' CASCADE';
        END LOOP;

-- drop all tables
    FOR r IN (SELECT t.table_name FROM information_schema.tables t
                                           LEFT JOIN pg_depend dep ON dep.objid = (quote_ident(t.table_schema)||'.'||quote_ident(t.table_name))::regclass::oid AND dep.deptype = 'e'
              WHERE t.table_schema=current_schema() AND table_type='BASE TABLE' AND dep.objid IS NULL
                AND NOT (SELECT EXISTS (SELECT inhrelid FROM pg_catalog.pg_inherits
                                        WHERE inhrelid = (quote_ident(t.table_schema)||'.'||quote_ident(t.table_name))::regclass::oid))) LOOP
            EXECUTE 'DROP TABLE ' || quote_ident(r.table_name) || ' CASCADE';
        END LOOP;

-- drop all statements for base types and created user types
    FOR r IN (SELECT typname, typcategory FROM pg_catalog.pg_type t LEFT JOIN pg_depend dep ON dep.objid = t.oid AND dep.deptype = 'e'
                    WHERE (t.typrelid = 0 OR (SELECT c.relkind = 'c' FROM pg_catalog.pg_class c WHERE c.oid = t.typrelid))
                         AND NOT EXISTS(SELECT 1 FROM pg_catalog.pg_type el WHERE el.oid = t.typelem AND el.typarray = t.oid)
                         AND t.typnamespace IN (SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = current_schema())
                         AND dep.objid IS NULL AND t.typtype != 'd') LOOP
            EXECUTE 'DROP TYPE IF EXISTS ' || quote_ident(current_schema()) || '.' || quote_ident(r.typname) || ' CASCADE';

            -- Only recreate Pseudo-types (P) and User-defined types (U)
            IF (r.typcategory == 'P' OR r.typcategory == 'U') THEN
                EXECUTE 'CREATE TYPE ' || quote_ident(current_schema()) || '.' || quote_ident(r.typname);
            END IF;
    END LOOP;

-- dropping all routines in this schema
    FOR r IN (SELECT proname, oidvectortypes(proargtypes) AS args,
                     CASE WHEN pg_proc.prokind='p' THEN 'PROCEDURE'
                          WHEN pg_proc.prokind='a' THEN 'AGGREGATE'
                          ELSE 'FUNCTION'
                     END as type
              FROM pg_proc INNER JOIN pg_namespace ns ON (pg_proc.pronamespace = ns.oid)
                           LEFT JOIN pg_depend dep ON dep.objid = pg_proc.oid AND dep.deptype = 'e'
              WHERE ns.nspname = current_schema() AND dep.objid IS NULL) LOOP

            EXECUTE 'DROP ' || r.type || ' IF EXISTS ' || quote_ident(current_schema()) || '.' || quote_ident(r.proname)
                        || '(' || r.args || ') CASCADE';
    END LOOP;

-- dropping the enums in this schema
    FOR r IN (SELECT t.typname FROM pg_catalog.pg_type t INNER JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = current_schema() AND t.typtype = 'e') LOOP
            EXECUTE 'DROP TYPE ' || quote_ident(current_schema()) || '.' || quote_ident(r.typname);
    END LOOP;

-- dropping the domains in this schema
    FOR r IN (SELECT t.typname as domain_name FROM pg_catalog.pg_type t LEFT JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
                    LEFT JOIN pg_depend dep ON dep.objid = t.oid AND dep.deptype = 'e'
                    WHERE t.typtype = 'd'  AND n.nspname = current_schema()  AND dep.objid IS NULL) LOOP

            EXECUTE 'DROP DOMAIN ' || quote_ident(current_schema()) || '.' || quote_ident(r.domain_name);
    END LOOP;

-- dropping the sequences in this schema
    FOR r IN (SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema=current_schema()) LOOP
            EXECUTE 'DROP TYPE ' || quote_ident(current_schema()) || '.' || quote_ident(r.sequence_name);
    END LOOP;

-- drop all statements for base types
    FOR r IN (SELECT typname, typcategory FROM pg_catalog.pg_type t LEFT JOIN pg_depend dep ON dep.objid = t.oid AND dep.deptype = 'e'
              WHERE (t.typrelid = 0 OR (SELECT c.relkind = 'c' FROM pg_catalog.pg_class c WHERE c.oid = t.typrelid))
                AND NOT EXISTS(SELECT 1 FROM pg_catalog.pg_type el WHERE el.oid = t.typelem AND el.typarray = t.oid)
                AND t.typnamespace IN (SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = current_schema())
                AND dep.objid IS NULL AND t.typtype != 'd') LOOP
            EXECUTE 'DROP TYPE IF EXISTS ' || quote_ident(current_schema()) || '.' || quote_ident(r.typname) || ' CASCADE';
    END LOOP;
END $$;