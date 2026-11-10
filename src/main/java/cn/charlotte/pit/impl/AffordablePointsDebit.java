package cn.charlotte.pit.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

/** Purchases must not use the administrative debit path that permits debt. */
public final class AffordablePointsDebit {
    private AffordablePointsDebit() {}

    public static boolean debit(Connection connection, UUID playerId, int amount) throws SQLException {
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE player_pointss SET points = points - ? WHERE player_uuid = ? AND points >= ?")) {
            statement.setInt(1, amount);
            statement.setString(2, playerId.toString());
            statement.setInt(3, amount);
            return statement.executeUpdate() == 1;
        }
    }
}
