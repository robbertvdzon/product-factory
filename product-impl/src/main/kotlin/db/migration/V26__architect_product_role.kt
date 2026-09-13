package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

/** Old checks were unnamed on H2 and named by PostgreSQL. Discover only the two role checks. */
class V26__architect_product_role : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val c = context.connection
        for ((table, column, clause) in listOf(
            Triple("pf_product_membership", "role", "role IN ('PRODUCT_OWNER','ARCHITECT')"),
            Triple("pf_user_account", "acting_role", "acting_role IS NULL OR acting_role IN ('FACTORY_OWNER','PRODUCT_OWNER','ARCHITECT')"),
        )) {
            val sql = """SELECT tc.constraint_name, cc.check_clause FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc ON cc.constraint_name=tc.constraint_name AND cc.constraint_schema=tc.constraint_schema
                WHERE LOWER(tc.table_name)='$table' AND tc.constraint_type='CHECK'"""
            val names = mutableListOf<String>()
            c.createStatement().use { st -> st.executeQuery(sql).use { rs -> while (rs.next()) {
                if (rs.getString(2).contains("PRODUCT_OWNER") && Regex("\\b$column\\b", RegexOption.IGNORE_CASE).containsMatchIn(rs.getString(2))) names += rs.getString(1)
            } } }
            c.createStatement().use { st ->
                names.forEach { st.execute("ALTER TABLE $table DROP CONSTRAINT \"${it.replace("\"", "\"\"")}\"") }
                st.execute("ALTER TABLE $table ADD CONSTRAINT ${table}_${column}_v2_check CHECK ($clause)")
            }
        }
    }
}
