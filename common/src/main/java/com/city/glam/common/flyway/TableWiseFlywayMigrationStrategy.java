package com.city.glam.common.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Table-wise migrations: {@code db/migration/<table>/V1__<table>.sql}, {@code V2__...} with ONE Flyway history per
 * table folder ({@code flyway_<folder>}), so every table has its own version numbers.
 * <p>
 * {@code db/migration/order.txt} lists the folders in execution order with, per folder, the version already applied
 * on a database that was migrated before this layout (it still has the service's old single history table, the
 * {@code spring.flyway.table}); such a database is baselined at that version once, nothing is run again.
 * <p>
 * Run: other locations of {@code spring.flyway.locations} first (test-only scripts, old history table), then pass 1
 * creates every table (version 1 of each folder, in order) and pass 2 applies the later versions of each folder,
 * so a change may reference any table.
 */
public class TableWiseFlywayMigrationStrategy implements FlywayMigrationStrategy {

    static final String ROOT = "db/migration";
    static final String ORDER_FILE = ROOT + "/order.txt";
    static final String HISTORY_PREFIX = "flyway_";

    private static final Logger log = LoggerFactory.getLogger(TableWiseFlywayMigrationStrategy.class);

    /** One line of order.txt. */
    record Folder(String name, int legacyVersion) {
        String historyTable() {
            return HISTORY_PREFIX + name.replaceFirst("^_+", "");
        }
    }

    @Override
    public void migrate(Flyway flyway) {
        Configuration base = flyway.getConfiguration();
        List<Folder> folders = readOrder(base.getClassLoader());
        // Measured BEFORE anything runs: a fresh database (tests) must never be baselined
        boolean legacy = tableExists(base, base.getTable());

        Location[] other = Arrays.stream(base.getLocations()).filter(l -> !isRoot(l)).toArray(Location[]::new);
        if (other.length > 0) {
            Flyway.configure(base.getClassLoader()).configuration(base).locations(other).load().migrate();
        }
        // What each folder has on the classpath and what its history already holds (one connection, cheap): Flyway
        // only runs for a folder that has something new, so a normal start does not open 2 x n Flyway runs
        Map<String, Integer> available = availableVersions(base.getClassLoader());
        Map<String, Integer> applied = appliedVersions(base, folders);
        // Every folder needs its own history before it can run in a schema that already has other tables: a new
        // folder starts at version 0 (its V1 then runs), a folder already applied under the old history starts at
        // the version written in order.txt (nothing runs twice)
        for (Folder f : folders) {
            if (applied.get(f.name()) < 0) {
                int start = legacy ? f.legacyVersion() : 0;
                if (start > 0) {
                    log.info("Flyway table-wise: {} baselined at version {} (applied before the table-wise layout)", f.name(), start);
                }
                configure(base, f).baselineVersion(String.valueOf(start))
                        .baselineDescription(start > 0 ? "applied before the table-wise layout" : "table-wise start").load().baseline();
                applied.put(f.name(), start);
            }
        }
        int ran = 0;
        for (Folder f : folders) {
            if (applied.get(f.name()) < 1 && available.getOrDefault(f.name(), 0) >= 1) {
                configure(base, f).target(MigrationVersion.fromVersion("1")).load().migrate();
                ran++;
            }
        }
        for (Folder f : folders) {
            if (Math.max(applied.get(f.name()), 1) < available.getOrDefault(f.name(), 0)) {
                configure(base, f).target(MigrationVersion.LATEST).load().migrate();
                ran++;
            }
        }
        log.info("Flyway table-wise: {} table folders, {} Flyway runs needed", folders.size(), ran);
    }

    /** Highest V<n> of each folder on the classpath. */
    static Map<String, Integer> availableVersions(ClassLoader classLoader) {
        Map<String, Integer> max = new HashMap<>();
        Pattern file = Pattern.compile(".*/" + ROOT + "/([^/]+)/V(\\d+)__[^/]+\\.sql$");
        try {
            for (Resource r : new PathMatchingResourcePatternResolver(classLoader).getResources("classpath*:" + ROOT + "/*/V*__*.sql")) {
                Matcher m = file.matcher(r.getURL().toString());
                if (m.matches()) {
                    max.merge(m.group(1), Integer.parseInt(m.group(2)), Math::max);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return max;
    }

    /** Highest applied (or baselined) version per folder; -1 = the folder has no history table yet. */
    private static Map<String, Integer> appliedVersions(Configuration base, List<Folder> folders) {
        Map<String, Integer> applied = new HashMap<>();
        String schema = schemaOf(base);
        try (Connection connection = base.getDataSource().getConnection()) {
            for (Folder f : folders) {
                boolean exists;
                try (ResultSet t = connection.getMetaData().getTables(null, schema, f.historyTable(), new String[]{"TABLE"})) {
                    exists = t.next();
                }
                if (!exists) {
                    applied.put(f.name(), -1);
                    continue;
                }
                String table = (schema == null ? "" : "\"" + schema + "\".") + "\"" + f.historyTable() + "\"";
                try (Statement st = connection.createStatement();
                     ResultSet rs = st.executeQuery("SELECT max(CAST(version AS integer)) FROM " + table + " WHERE success AND version IS NOT NULL")) {
                    applied.put(f.name(), rs.next() ? rs.getInt(1) : 0);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read the table-wise Flyway histories", e);
        }
        return applied;
    }

    private static String schemaOf(Configuration base) {
        return base.getDefaultSchema() != null ? base.getDefaultSchema() : base.getSchemas().length > 0 ? base.getSchemas()[0] : null;
    }

    private static FluentConfiguration configure(Configuration base, Folder f) {
        return Flyway.configure(base.getClassLoader()).configuration(base)
                .locations("classpath:" + ROOT + "/" + f.name())
                .table(f.historyTable())
                .baselineOnMigrate(false);
    }

    private static boolean isRoot(Location location) {
        return location.isClassPath() && ROOT.equals(location.getRootPath());
    }

    static List<Folder> readOrder(ClassLoader classLoader) {
        InputStream in = classLoader.getResourceAsStream(ORDER_FILE);
        if (in == null) {
            throw new IllegalStateException("Missing " + ORDER_FILE + " (table-wise Flyway)");
        }
        List<Folder> folders = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                folders.add(new Folder(parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 0));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return folders;
    }

    private static boolean tableExists(Configuration base, String table) {
        String schema = base.getDefaultSchema() != null ? base.getDefaultSchema()
                : base.getSchemas().length > 0 ? base.getSchemas()[0] : null;
        try (Connection connection = base.getDataSource().getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, schema, table.toLowerCase(), new String[]{"TABLE"})) {
            return tables.next();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot check for table " + table, e);
        }
    }
}
