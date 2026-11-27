package com.city.glam.common.flyway;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;

/**
 * Table-wise Flyway for every service that has a {@code db/migration/order.txt}: one folder and one history per
 * table (see {@link TableWiseFlywayMigrationStrategy}). A service without that file keeps plain Flyway.
 */
@AutoConfiguration(before = FlywayAutoConfiguration.class)
@ConditionalOnClass({Flyway.class, FlywayMigrationStrategy.class})
@ConditionalOnResource(resources = "classpath:" + TableWiseFlywayMigrationStrategy.ORDER_FILE)
public class TableWiseFlywayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FlywayMigrationStrategy.class)
    FlywayMigrationStrategy tableWiseFlywayMigrationStrategy() {
        return new TableWiseFlywayMigrationStrategy();
    }
}
