package com.fraz.dartlog.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.fraz.dartlog.game.Game;
import com.fraz.dartlog.game.GameData;
import com.fraz.dartlog.game.PlayerData;
import com.fraz.dartlog.game.x01.X01;
import com.fraz.dartlog.game.x01.X01PlayerData;
import com.fraz.dartlog.game.x01.X01ScoreManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

public class DartLogDatabaseHelper extends SQLiteOpenHelper {

    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "DartLog.db";

    public DartLogDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String createSql : DartLogContract.SQL_CREATE_ENTRIES) {
            db.execSQL(createSql);
        }

        initializePlayers(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        resetDatabase();
    }

    public void resetDatabase() {
        try (SQLiteDatabase db = getWritableDatabase()) {
            for (String deleteSql : DartLogContract.SQL_DELETE_ENTRIES) {
                db.execSQL(deleteSql);
            }
            for (String createSql : DartLogContract.SQL_CREATE_ENTRIES) {
                db.execSQL(createSql);
            }

            initializePlayers(db);
        }
    }

    /**
     * Add a player to the database. Duplicated names is not allowed.
     *
     * @param name the name of the player to add.
     * @return the row ID of the newly inserted player, or -1 if the player could not be added.
     */
    public long addPlayer(String name) {
        try (SQLiteDatabase db = getWritableDatabase()) {
            return addPlayer(name, db);
        }
    }

    /**
     * Add a player to the database. Duplicated names is not allowed.
     *
     * @param name the name of the player to add.
     * @param db   the database which the name shall be added to.
     * @return the row ID of the newly inserted player, or -1 if the player could not be added.
     */
    private long addPlayer(String name, SQLiteDatabase db) {
        ContentValues values = new ContentValues();
        values.put(DartLogContract.PlayerEntry.COLUMN_NAME_PLAYER_NAME, name);
        return db.insert(DartLogContract.PlayerEntry.TABLE_NAME, null, values);
    }

    /**
     * Get the player names of all the players.
     *
     * @return the player names of all the players in the database.
     */
    public ArrayList<String> getPlayers() {
        ArrayList<String> names;
        try (SQLiteDatabase db = getReadableDatabase()) {
            String[] projection = {
                    DartLogContract.PlayerEntry.COLUMN_NAME_PLAYER_NAME
            };

            names = new ArrayList<>();
            try (Cursor c = db.query(DartLogContract.PlayerEntry.TABLE_NAME, projection,
                    null, null, null, null, null)) {
                while (c.moveToNext()) {
                    names.add(c.getString(c.getColumnIndex(
                            DartLogContract.PlayerEntry.COLUMN_NAME_PLAYER_NAME)));
                }
            }
        }
        return names;
    }

    /**
     * Get all match data for the player with the given name.
     *
     * @param playerName The name of the player.
     * @return List of match data for the given player.
     */
    public ArrayList<GameData> getPlayerMatchData(String playerName) {
        ArrayList<GameData> gameData;
        try (SQLiteDatabase db = getReadableDatabase()) {

            long playerId = getPlayerId(db, playerName);
            ArrayList<Long> matchIds = getMatchIds(db, playerId);

            gameData = new ArrayList<>();
            for (long matchId : matchIds) {
                gameData.add(getGameData(db, matchId));
            }
        }
        return gameData;
    }

    private GameData getGameData(SQLiteDatabase db, long matchId) {
        HashMap<String, LinkedList<Integer>> matchScores = getMatchScores(db, matchId);
        LinkedHashMap<String, PlayerData> playerData = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedList<Integer>> playerEntry : matchScores.entrySet())
        {
            X01ScoreManager scoreManager = new X01ScoreManager(3, playerEntry.getValue());
            playerData.put(playerEntry.getKey(),
                           new X01PlayerData(playerEntry.getKey(), scoreManager));
        }

        try (Cursor c = getX01MatchEntry(db, matchId)) {
            if (c.moveToFirst()) {
                long dateInMillis = c.getLong(c.getColumnIndex(DartLogContract.MatchEntry.COLUMN_NAME_DATE));
                String winnerName = c.getString(c.getColumnIndex("winner"));
                Calendar date = Calendar.getInstance();
                date.setTimeInMillis(dateInMillis);
                return new GameData(new ArrayList<>(playerData.values()),
                        date, playerData.get(winnerName));
            } else throw new IllegalArgumentException("Match not found");
        }
    }

    /**
     * Add a match to the database. Date at time of the add is recorded as date of match.
     * All players scores are added.
     *
     * @param game The match to add.
     */
    public void addX01Match(X01 game) {
        try (SQLiteDatabase db = getWritableDatabase()) {
            long matchId = insertX01MatchEntry(db, game);

            for (int i = 0; i < game.getNumberOfPlayers(); ++i) {
                insertPlayerScores(db, game.getPlayer(i), matchId);
            }
        }
    }

    private void insertPlayerScores(SQLiteDatabase db, PlayerData player, long matchId) {
        long playerId = getPlayerId(db, player);

        for (int score : player.getScoreHistory()) {
            ContentValues values = new ContentValues();
            values.put(DartLogContract.ScoreEntry.COLUMN_NAME_SCORE, score);
            values.put(DartLogContract.ScoreEntry.COLUMN_NAME_MATCH_ID, matchId);
            values.put(DartLogContract.ScoreEntry.COLUMN_NAME_PLAYER_ID, playerId);
            db.insert(DartLogContract.ScoreEntry.TABLE_NAME, null, values);
        }
    }

    private HashMap<String, LinkedList<Integer>> getMatchScores(SQLiteDatabase db, long matchId) {
        String sql = "SELECT p.name, s.score " +
                "          FROM match_score s " +
                "              join player p " +
                "                  on s.player_id = p._ID and s.match_id = ?;";

        HashMap<String, LinkedList<Integer>> playerScores = new HashMap<>();

        try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(matchId)})) {
            while (c.moveToNext()) {
                int score = c.getInt(c.getColumnIndex(
                        DartLogContract.ScoreEntry.COLUMN_NAME_SCORE));
                String playerName = c.getString(c.getColumnIndex(
                        DartLogContract.PlayerEntry.COLUMN_NAME_PLAYER_NAME));
                if (playerScores.containsKey(playerName))
                    playerScores.get(playerName).add(score);
                else {
                    LinkedList<Integer> scores = new LinkedList<>();
                    scores.add(score);
                    playerScores.put(playerName, scores);
                }
            }
        }
        return playerScores;
    }

    /**
     * Get the ids of all the matches the given player has played.
     *
     * @param playerId The id of the player.
     * @return List of match ids.
     */
    private ArrayList<Long> getMatchIds(SQLiteDatabase db, long playerId) {
        ArrayList<Long> matchIds = new ArrayList<>();

        try (Cursor c = db.query(true, DartLogContract.ScoreEntry.TABLE_NAME,
                new String[]{DartLogContract.ScoreEntry.COLUMN_NAME_MATCH_ID},
                String.format(Locale.getDefault(), "%s = '%d'",
                        DartLogContract.ScoreEntry.COLUMN_NAME_PLAYER_ID,
                        playerId),
                null, null, null, null, null)) {
            while (c.moveToNext()) {
                matchIds.add(c.getLong(c.getColumnIndex(
                        DartLogContract.ScoreEntry.COLUMN_NAME_MATCH_ID)));
            }
        }
        return matchIds;
    }

    /**
     * Gets the ID of a player in the database. Adds a new player if id does not exist.
     *
     * @param db     The database.
     * @param player The player to find in the database or add if not existing.
     * @return The id of the player in the database.
     */
    private long getPlayerId(SQLiteDatabase db, PlayerData player) {
        return getPlayerId(db, player.getPlayerName());
    }

    /**
     * Gets the ID of a player in the database. Adds a new player if id does not exist.
     *
     * @param db         The database.
     * @param playerName The name of the player to find in the database or add if not existing.
     * @return The id of the player in the database.
     */
    private long getPlayerId(SQLiteDatabase db, String playerName) {
        long playerId;
        try (Cursor c = db.query(DartLogContract.PlayerEntry.TABLE_NAME,
                new String[]{DartLogContract.PlayerEntry._ID},
                String.format("%s = '%s'", DartLogContract.PlayerEntry.COLUMN_NAME_PLAYER_NAME,
                        playerName), null, null, null, null)) {
            if (c.getCount() == 0)
                playerId = addPlayer(playerName);
            else {
                c.moveToFirst();
                playerId = c.getLong(0);
            }
        }
        return playerId;
    }

    private long insertX01MatchEntry(SQLiteDatabase db, Game game) {
        ContentValues matchValues = new ContentValues();
        matchValues.put(DartLogContract.MatchEntry.COLUMN_NAME_DATE,
                game.getDate().getTimeInMillis());
        matchValues.put(DartLogContract.MatchEntry.COLUMN_NAME_GAME, "X01");
        matchValues.put(DartLogContract.MatchEntry.COLUMN_NAME_WINNER_PLAYER_ID,
                getPlayerId(db, game.getWinner()));
        return db.insert(DartLogContract.MatchEntry.TABLE_NAME, null, matchValues);
    }

    private Cursor getX01MatchEntry(SQLiteDatabase db, long matchId) {
        String sql = "SELECT m.date, w.name as winner" +
                "     FROM match m" +
                "          join player w" +
                "               on w._ID = m.winner_id" +
                "     WHERE m._ID = ?;";

        return db.rawQuery(sql, new String[]{String.valueOf(matchId)});
    }

    private void initializePlayers(SQLiteDatabase db) {
        ArrayList<String> playersNames = new ArrayList<>();

        playersNames.add("Razmus");
        playersNames.add("Filip");
        playersNames.add("Jonathan");
        playersNames.add("Martin");
        playersNames.add("Erik");
        playersNames.add("Fredrik");
        playersNames.add("Stefan");
        playersNames.add("Maria");
        playersNames.add("Gustav");

        for (String name : playersNames) {
            addPlayer(name, db);
        }
    }
}
