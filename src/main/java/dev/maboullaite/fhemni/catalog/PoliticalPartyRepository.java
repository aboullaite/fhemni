package dev.maboullaite.fhemni.catalog;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads the curated party reference table owned by Flyway.
 */
@Repository
public class PoliticalPartyRepository {

    private final JdbcClient jdbc;

    public PoliticalPartyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<PoliticalParty> findAll() {
        return jdbc.sql("""
                        SELECT code, name_fr, name_ar, color,
                               symbol_label_fr, symbol_label_ar, symbol_asset,
                               symbol_verified, catalogue_code, visible
                          FROM political_parties
                         ORDER BY sort_order
                        """)
                .query((resultSet, rowNumber) -> new PoliticalParty(
                        resultSet.getString("code"),
                        resultSet.getString("name_fr"),
                        resultSet.getString("name_ar"),
                        resultSet.getString("color"),
                        resultSet.getString("symbol_label_fr"),
                        resultSet.getString("symbol_label_ar"),
                        resultSet.getString("symbol_asset"),
                        resultSet.getBoolean("symbol_verified"),
                        resultSet.getString("catalogue_code"),
                        resultSet.getBoolean("visible")))
                .list();
    }
}
