# Database Migration Strategy for License Plate Template Feature

## Overview
The license plate template feature introduces database schema changes from version 3 to version 4, adding new entities for countries and license plate templates with complex relationships and validation rules.

## Migration Details

### Schema Changes (v3 → v4)
- **New Tables:**
  - `countries`: Country configuration with metadata
  - `license_plate_templates`: Template definitions with validation patterns
- **Relationships:**
  - Foreign key from `license_plate_templates.country_id` to `countries.id`
  - Cascade delete to maintain referential integrity
- **Indexes:**
  - Performance index on `country_id`
  - Unique constraint on `country_id + priority` combination

## Migration Safety Strategy

### Pre-Migration Validation
```sql
-- Verify current schema version
SELECT version FROM room_master_table;

-- Check data integrity before migration
SELECT COUNT(*) FROM watchlist_entries;
SELECT COUNT(DISTINCT country) FROM watchlist_entries;
```

### Migration Rollback Plan
```sql
-- Emergency rollback from v4 to v3 (if needed)
-- Note: This will lose template data but preserve watchlist data
DROP TABLE IF EXISTS license_plate_templates;
DROP TABLE IF EXISTS countries;
-- Schema version handled by Room automatically
```

### Post-Migration Verification
```sql
-- Verify new tables exist
SELECT name FROM sqlite_master WHERE type='table' AND name IN ('countries', 'license_plate_templates');

-- Verify foreign key constraints
PRAGMA foreign_key_check;

-- Verify default data insertion
SELECT COUNT(*) FROM countries;
SELECT COUNT(*) FROM license_plate_templates WHERE country_id IN ('ISRAEL', 'UK');
```

## Deployment Strategy

### Phase 1: Migration Testing
1. **Local Testing:**
   - Test migration on development databases
   - Verify with different data scenarios (empty, populated, edge cases)
   - Performance test migration time with large datasets

2. **Staging Environment:**
   - Deploy to staging with production-like data volumes
   - Monitor migration duration and resource usage
   - Validate all existing functionality still works

### Phase 2: Production Deployment
1. **Pre-deployment Checklist:**
   ```bash
   # Verify migration tests pass
   ./gradlew testDebugUnitTest -Pandroid.testInstrumentationRunnerArguments.class=*MigrationTest*
   
   # Check database backup capability (if applicable)
   # Ensure rollback plan is ready
   ```

2. **Deployment Monitoring:**
   - Monitor app crash rates during migration
   - Track migration completion rates
   - Watch for performance degradation

3. **Feature Flag Strategy:**
   ```kotlin
   // Use feature flags to control template feature availability
   if (BuildConfig.ENABLE_FEATURE_FLAGS && FeatureFlags.LICENSE_PLATE_TEMPLATES_ENABLED) {
       // Enable template functionality
   } else {
       // Fall back to legacy behavior
   }
   ```

## Migration Performance Considerations

### Expected Migration Time
- Small databases (< 1000 entries): < 100ms
- Medium databases (1000-10000 entries): 100ms-1s
- Large databases (> 10000 entries): 1s-5s

### Memory Usage
- Migration adds minimal memory overhead
- Template data is loaded on-demand
- Indexes improve query performance but slightly increase storage

## Error Handling Strategy

### Migration Failure Scenarios
1. **Insufficient Storage:**
   ```kotlin
   try {
       // Migration code
   } catch (SQLiteFullException e) {
       // Handle storage full scenario
       Analytics.track("migration_failed_storage_full")
   }
   ```

2. **Data Integrity Issues:**
   ```kotlin
   try {
       // Migration code
   } catch (SQLiteConstraintException e) {
       // Handle constraint violations
       Analytics.track("migration_failed_constraint_violation")
   }
   ```

3. **Timeout/Performance:**
   - Migration wrapped in transaction for atomicity
   - Timeout protection to prevent ANR
   - Graceful degradation if migration fails

## Monitoring and Alerting

### Key Metrics to Monitor
- Migration success/failure rates
- Migration duration percentiles (p50, p95, p99)
- Post-migration app crash rates
- Database query performance impact

### Alert Thresholds
- Migration failure rate > 1%
- Migration duration > 10 seconds (p99)
- Post-migration crash rate increase > 5%

## Gradual Rollout Plan

### Phase 1: Internal Testing (0.1% users)
- Deploy to internal testers
- Monitor for 24 hours
- Verify all metrics within thresholds

### Phase 2: Staged Rollout (1% → 10% → 50% → 100%)
- Gradual increase every 24-48 hours
- Stop rollout if any metric exceeds threshold
- Full rollback capability at each stage

### Feature Flag Configuration
```kotlin
object FeatureFlags {
    const val LICENSE_PLATE_TEMPLATES_ENABLED = BuildConfig.ENABLE_FEATURE_FLAGS
    
    fun isTemplateFeatureEnabled(): Boolean {
        return LICENSE_PLATE_TEMPLATES_ENABLED && 
               // Additional runtime checks if needed
               isUserInTemplateRollout()
    }
}
```

## Validation and Testing

### Automated Tests
- Migration unit tests cover all scenarios
- Integration tests verify end-to-end functionality
- Performance tests ensure acceptable migration times

### Manual Testing Checklist
- [ ] Fresh install works correctly
- [ ] Migration from v3 to v4 successful
- [ ] All existing watchlist data preserved
- [ ] Template functionality works as expected
- [ ] Settings UI correctly displays templates
- [ ] Camera validation uses templates properly

## Documentation for Support Team

### Common Migration Issues
1. **Migration Timeout:** 
   - Restart app, migration will retry
   - If persistent, clear app data (loses user data)

2. **Template Feature Not Working:**
   - Check feature flag status
   - Verify migration completed successfully
   - Clear app cache and restart

3. **Performance Issues:**
   - Migration adds minimal overhead
   - If issues persist, investigate other causes
   - Consider disabling templates temporarily via feature flag

### Database Schema Reference
```sql
-- Countries table schema
CREATE TABLE countries (
    id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    flag_resource_id TEXT NOT NULL,
    is_enabled INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0
);

-- License plate templates table schema
CREATE TABLE license_plate_templates (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    country_id TEXT NOT NULL,
    template_pattern TEXT NOT NULL,
    display_name TEXT NOT NULL,
    priority INTEGER NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 1,
    description TEXT NOT NULL,
    regex_pattern TEXT NOT NULL,
    created_at INTEGER NOT NULL DEFAULT 0,
    updated_at INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(country_id) REFERENCES countries(id) ON DELETE CASCADE
);
```