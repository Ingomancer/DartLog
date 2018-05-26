package com.fraz.dartlog.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseLongArray;

import com.fraz.dartlog.game.AdditionScoreManager;
import com.fraz.dartlog.game.Game;
import com.fraz.dartlog.game.GameData;
import com.fraz.dartlog.game.PlayerData;
import com.fraz.dartlog.game.random.Random;
import com.fraz.dartlog.game.x01.X01;
import com.fraz.dartlog.game.x01.X01PlayerData;
import com.fraz.dartlog.game.x01.X01ScoreManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

public class DartLogDatabaseHelper extends SQLiteOpenHelper {

    // If you change the database schema, you must increment the database version.
    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "DartLog.db";
    private Context context;

    public DartLogDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
    }

    public DartLogDatabaseHelper(Context context, String databaseName){
        super(context, databaseName, null, DATABASE_VERSION);
        this.context = context;
        Log.d("dbHelper", "successfully opened database "+ databaseName);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String createSql : DartLogContract.SQL_CREATE_ENTRIES) {
            db.execSQL(createSql);
        }
        db.execSQL(DartLogContract.SQL_CREATE_STATISTIC_ENTRIES );
        initializePlayers(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        switch (newVersion) {
            case 1:
                db.execSQL("DROP TABLE IF EXISTS " + DartLogContract.StatisticEntry.TABLE_NAME);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        //resetDatabase();
        switch (oldVersion) {
            case 1:
                db.execSQL(DartLogContract.SQL_CREATE_STATISTIC_ENTRIES);
        }
    }

    private void resetDatabase() {
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

    public HashMap<Long, GameData> getPlayerMatchDataById(String playerName) {
        HashMap<Long, GameData> gameDataById;
        try (SQLiteDatabase db = getReadableDatabase()) {

            long playerId = getPlayerId(db, playerName);
            ArrayList<Long> matchIds = getAllMatchIds(db, playerId);
            HashMap<Long, String> gameTypes = new HashMap<>();
            for(long matchId : matchIds)
                gameTypes.put(matchId, getGameType(db, matchId));

            gameDataById = new HashMap<>();
            for (long matchId : matchIds) {
                String gameType = gameTypes.get(matchId);

                switch (gameType){
                    case "x01":
                        gameDataById.put(matchId, getX01GameData(db, matchId));
                        break;
                    case "random":
                        gameDataById.put(matchId, getRandomGameData(db, matchId));
                        break;

                }
            }
        }
        return gameDataById;
    }

    /**
     * Get all match data for the player with the given name.
     *
     * @param playerName The name of the player.
     * @return List of match data for the given player.
     */
    public ArrayList<GameData> getPlayerMatchData(String playerName) {
        return new ArrayList<>(getPlayerMatchDataById(playerName).values());
    }

    private long lastLoadedMatchId = Long.MAX_VALUE;

    /**
     *
     * @param playerName The name of the player.
     * @param startMatchId The starting match id in the database - OBS! the data is fetched backwards.
     * @param amount
     * @return List of match data for the given player.
     */
    public ArrayList<GameData> getPlayerMatchData(String playerName, long startMatchId, int amount) {
        ArrayList<GameData> gameData;
        try (SQLiteDatabase db = getReadableDatabase()) {

            long playerId = getPlayerId(db, playerName);
            ArrayList<Long> matchIds = getMatchIds(db, playerId, startMatchId, amount);

            if (matchIds.size() > 0)
                lastLoadedMatchId = matchIds.get(matchIds.size() - 1);
            HashMap<Long, String> gameTypes = new HashMap<>();
            for(long matchId : matchIds)
                gameTypes.put(matchId, getGameType(db, matchId));

            gameData = new ArrayList<>();
            for (long MatchId : matchIds) {
                String gameType = gameTypes.get(MatchId);

                switch (gameType){
                    case "x01":
                        gameData.add(getX01GameData(db, MatchId));
                        break;
                    case "random":
                        gameData.add(getRandomGameData(db, MatchId));
                        break;
                }
            }
        }
        return gameData;
    }

    public int getNumberOfGamesPlayed(String playerName) {
        try (SQLiteDatabase db = getReadableDatabase()) {
            long playerId = getPlayerId(db, playerName);
            Cursor c = db.query(true, DartLogContract.ScoreEntry.TABLE_NAME,
                    new String[]{DartLogContract.ScoreEntry.COLUMN_NAME_MATCH_ID},
                    String.format(Locale.getDefault(), "%s = '%d'",
                            DartLogContract.ScoreEntry.COLUMN_NAME_PLAYER_ID, playerId),
                    null, null, null, null, null);
            int count = c.getCount();
            c.close();
            return count;
        }
    }

    public int getNumberOfGamesWon(String playerName) {
        try (SQLiteDatabase db = getReadableDatabase()) {

            long playerId = getPlayerId(db, playerName);
            String query = "SELECT m._ID" +
                          "     FROM \"match\" m" +
                          "          join player p" +
                          "               on p._ID = m.winner_id" +
                          "     WHERE m.winner_id = ?;";

            Cursor c = db.rawQuery(query, new String[]{String.valueOf(playerId)});
            return c.getCount();
        }
    }

    private GameData getRandomGameData(SQLiteDatabase db, long matchId) {
        HashMap<String, LinkedList<Integer>> matchScores = getMatchScores(db, matchId);

        try (Cursor c = getRandomMatchEntry(db, matchId)) {
            if (c.moveToFirst()) {
                String winnerName = c.getString(c.getColumnIndex("winner"));
                Calendar date = getDate(c);

                LinkedHashMap<String, PlayerData> playerData = new LinkedHashMap<>();
                for (Map.Entry<String, LinkedList<Integer>> playerEntry : matchScores.entrySet())
                {
                    AdditionScoreManager scoreManager = new AdditionScoreManager();
                    scoreManager.applyScores(playerEntry.getValue());
                    playerData.put(playerEntry.getKey(),
                            new PlayerData(playerEntry.getKey(), scoreManager));
                }

                return new GameData(new ArrayList<>(playerData.values()),
                        date, playerData.get(winnerName), "random");
            } else throw new IllegalArgumentException("Match not found");
        }
    }

    private GameData getX01GameData(SQLiteDatabase db, long matchId) {
        HashMap<String, LinkedList<Integer>> matchScores = getMatchScores(db, matchId);

        try (Cursor c = getX01MatchEntry(db, matchId)) {
            if (c.moveToFirst()) {
                String winnerName = c.getString(c.getColumnIndex("winner"));
                Calendar date = getDate(c);
                int x = c.getInt(c.getColumnIndex(DartLogContract.X01Entry.COLUMN_NAME_X));
                int double_out =
                        c.getInt(c.getColumnIndex(DartLogContract.X01Entry.COLUMN_NAME_DOUBLE_OUT));

                LinkedHashMap<String, PlayerData> playerData = new LinkedHashMap<>();
                for (Map.Entry<String, LinkedList<Integer>> playerEntry : matchScores.entrySet())
                {
                    X01ScoreManager scoreManager = new X01ScoreManager(x);
                    scoreManager.setDoubleOutAttempts(double_out);
                    scoreManager.applyScores(playerEntry.getValue());
                    playerData.put(playerEntry.getKey(),
                                   new X01PlayerData(context, playerEntry.getKey(), scoreManager));
                }


                return new GameData(new ArrayList<>(playerData.values()),
                        date, playerData.get(winnerName), "x01");
            } else throw new IllegalArgumentException("Match not found");
        }
    }

    public GameData getHighestCheckoutGame(String playerName) {
        GameData highestCheckoutMatch = null;
        try (SQLiteDatabase db = getWritableDatabase()) {
            Long matchId = getStatisticsEntry(db, playerName, DartLogContract.StatisticEntry.COLUMN_NAME_HIGHEST_CHECKOUT_MATCH_ID);
            if (matchId != null) {
                highestCheckoutMatch = getX01GameData(db, matchId);
            }
        }
        return highestCheckoutMatch;
    }

    public GameData getFewestTurns301Game(String playerName) {
        GameData highestCheckoutMatch = null;
        try (SQLiteDatabase db = getWritableDatabase()) {
            Long matchId = getStatisticsEntry(db, playerName, DartLogContract.StatisticEntry.COLUMN_NAME_301_FEWEST_TURNS_MATCH_ID);
            if (matchId != null) {
                highestCheckoutMatch = getX01GameData(db, matchId);
            }
        }
        return highestCheckoutMatch;
    }

    public GameData getFewestTurns501Game(String playerName) {
        GameData highestCheckoutMatch = null;
        try (SQLiteDatabase db = getWritableDatabase()) {
            Long matchId = getStatisticsEntry(db, playerName, DartLogContract.StatisticEntry.COLUMN_NAME_501_FEWEST_TURNS_MATCH_ID);
            if (matchId != null) {
                highestCheckoutMatch = getX01GameData(db, matchId);
            }
        }
        return highestCheckoutMatch;
    }

    private Long getStatisticsEntry(SQLiteDatabase db, String playerName, String columnId) {
        Long matchId = null;
        long playerId = getPlayerId(db, playerName);
        Cursor c = db.query(DartLogContract.StatisticEntry.TABLE_NAME,
                new String[]{columnId},
                String.format("%s = %s", DartLogContract.StatisticEntry.COLUMN_NAME_PLAYER_ID,
                        playerId), null, null, null, null);
        c.moveToFirst();
        int columnIndex = c.getColumnIndex(columnId);
        if (c.getCount() > 0 && !c.isNull(columnIndex)) {
            matchId = c.getLong(columnIndex);
        }
        c.close();
        return matchId;
    }

    public void refreshStatistics(String playerName) {
        HashMap<Long, GameData> playerGameData = getPlayerMatchDataById(playerName);

        Long highestOutMatchId = getHighestCheckoutMatchId(playerName, playerGameData);
        HashMap<String, Long> fewestTurnsMatchIds =
                getFewestTurnsMatchIds(playerName, playerGameData);

        Long fewestTurns301MatchId = fewestTurnsMatchIds.get("301");
        Long fewestTurns501MatchId = fewestTurnsMatchIds.get("501");
        try (SQLiteDatabase db = getWritableDatabase()) {
            long playerId = getPlayerId(db, playerName);
            if (fewestTurns301MatchId != null) {
                updateStatisticsEntry(
                        db,
                        playerId,
                        DartLogContract.StatisticEntry.COLUMN_NAME_301_FEWEST_TURNS_MATCH_ID,
                        fewestTurns301MatchId);
            }
            if (fewestTurns501MatchId != null) {
                updateStatisticsEntry(
                        db,
                        playerId,
                        DartLogContract.StatisticEntry.COLUMN_NAME_501_FEWEST_TURNS_MATCH_ID,
                        fewestTurns501MatchId);
            }
            if (highestOutMatchId != null) {
                updateStatisticsEntry(
                        db,
                        playerId,
                        DartLogContract.StatisticEntry.COLUMN_NAME_HIGHEST_CHECKOUT_MATCH_ID,
                        highestOutMatchId);
            }
        }
    }

    @Nullable
    private Long getHighestCheckoutMatchId(String playerName, HashMap<Long, GameData> playerGameData) {
        int highestOut = Integer.MIN_VALUE;
        Long highestOutMatchId = null;
        for (Map.Entry<Long, GameData> entry : playerGameData.entrySet()) {
            long matchId = entry.getKey();
            GameData game = entry.getValue();
            if(game.getGameType().equals("x01") &&
                    game.getWinner().getPlayerName().equals(playerName))
            {
                int out = game.getWinner().getScoreHistory().getLast();
                if (out > highestOut)
                {
                    highestOutMatchId = matchId;
                    highestOut = out;
                }
            }
        }
        return highestOutMatchId;
    }

    private HashMap<String, Long> getFewestTurnsMatchIds(String playerName, HashMap<Long, GameData> playerGameData) {
        HashMap<String, Integer> fewestTurns = new HashMap<>();
        HashMap<String, Long> fewestTurnsMatchIds = new HashMap<>();
        for (Map.Entry<Long, GameData> entry : playerGameData.entrySet()) {
            long matchId = entry.getKey();
            GameData game = entry.getValue();
            if(game.getGameType().equals("x01") &&
               game.getWinner().getPlayerName().equals(playerName))
            {
                String detailedGameType = game.getDetailedGameType();
                int turns = game.getTurns();
                if (fewestTurnsMatchIds.containsKey(detailedGameType))
                {
                    if (turns < fewestTurns.get(detailedGameType)) {
                        fewestTurnsMatchIds.put(detailedGameType, matchId);
                        fewestTurns.put(detailedGameType, turns);
                    }
                } else {
                    fewestTurnsMatchIds.put(detailedGameType, matchId);
                    fewestTurns.put(detailedGameType, turns);
                }
            }
        }
        return fewestTurnsMatchIds;
    }

    private void updateStatisticsEntry(SQLiteDatabase db, long playerId, String columnId, long matchId) {
        ContentValues newValues = new ContentValues();
        newValues.put(columnId, matchId);
        int rowsUpdated = db.update(DartLogContract.StatisticEntry.TABLE_NAME,
                newValues,
                String.format("%s = %s", DartLogContract.StatisticEntry.COLUMN_NAME_PLAYER_ID,
                    playerId),
                null);
        if (rowsUpdated == 0){
            newValues.put(DartLogContract.StatisticEntry.COLUMN_NAME_PLAYER_ID, playerId);
            db.insert(DartLogContract.StatisticEntry.TABLE_NAME, null,
                    newValues);
        }
    }

    @NonNull
    private Calendar getDate(Cursor c) {
        long dateInMillis = c.getLong(c.getColumnIndex(DartLogContract.Match.COLUMN_NAME_DATE));
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(dateInMillis);
        return date;
    }

    public long getLastLoadedMatchId(){
        return lastLoadedMatchId;
    }

    /**
     * Add a match to the database. Date at time of the add is recorded as date of match.
     * All players scores are added.
     *
     * @param game The match to add.
     */
    public void addX01Match(X01 game) {
        try (SQLiteDatabase db = getWritableDatabase()) {
            long matchId = insertMatchEntry(db, game, "x01");
            insertX01Entry(db, game, matchId);
            insertScores(db, game, matchId);

            // Add new statistics for player.
            String playerName = game.getWinner().getPlayerName();
            int checkout = game.getWinner().getScoreHistory().getLast();
            long playerId = getPlayerId(db, playerName);
            Long currentCheckoutMatchId = getStatisticsEntry(db, playerName,
                    DartLogContract.StatisticEntry.COLUMN_NAME_HIGHEST_CHECKOUT_MATCH_ID);
            if (currentCheckoutMatchId  == null || checkout > getX01GameData(db, currentCheckoutMatchId).getCheckout()) {
                updateStatisticsEntry(db,
                        playerId,
                        DartLogContract.StatisticEntry.COLUMN_NAME_HIGHEST_CHECKOUT_MATCH_ID,
                        matchId);
            }
            int turns = game.getTurn();
            if (game.getX() == 3) {
                Long current301TurnsMatchId = getStatisticsEntry(db, playerName,
                        DartLogContract.StatisticEntry.COLUMN_NAME_301_FEWEST_TURNS_MATCH_ID);
                if (current301TurnsMatchId  == null || turns < getX01GameData(db, current301TurnsMatchId).getTurns()) {
                    updateStatisticsEntry(db,
                            playerId,
                            DartLogContract.StatisticEntry.COLUMN_NAME_301_FEWEST_TURNS_MATCH_ID,
                            matchId);
                }
            } else if(game.getX() == 5) {
                Long fewestTurns501MatchId = getStatisticsEntry(db, playerName,
                        DartLogContract.StatisticEntry.COLUMN_NAME_501_FEWEST_TURNS_MATCH_ID);
                if (fewestTurns501MatchId == null || turns < getX01GameData(db, fewestTurns501MatchId).getTurns()) {
                    updateStatisticsEntry(db,
                        playerId,
                        DartLogContract.StatisticEntry.COLUMN_NAME_501_FEWEST_TURNS_MATCH_ID,
                        matchId);
                }
            }
        }
    }

    /**
     * Add a match to the database. Date at time of the add is recorded as date of match.
     * All players scores are added.
     *
     * @param game The match to add.
     */
    public void addRandomMatch(Random game) {
        try (SQLiteDatabase db = getWritableDatabase()) {
            long matchId = insertMatchEntry(db, game, "random");
            insertRandomEntry(db, game, matchId);
            insertScores(db, game, matchId);
        }
    }

    private void insertScores(SQLiteDatabase db, Game game, long matchId) {
        SparseLongArray playerIds = new SparseLongArray();
        SparseArray<Iterator<Integer>> playerScoreIterators = new SparseArray<>();

        for (int i = 0; i < game.getNumberOfPlayers(); i++) {
            PlayerData player = game.getPlayer(i);
            long playerId = getPlayerId(db, player);
            playerIds.append(i, playerId);
            playerScoreIterators.append(i, player.getScoreHistory().iterator());
        }

        for (Integer i : game.getPlayOrder()) {
            int score = playerScoreIterators.get(i).next();
            ContentValues values = new ContentValues();
            values.put(DartLogContract.ScoreEntry.COLUMN_NAME_SCORE, score);
            values.put(DartLogContract.ScoreEntry.COLUMN_NAME_MATCH_ID, matchId);
            values.put(DartLogContract.ScoreEntry.COLUMN_NAME_PLAYER_ID, playerIds.get(i));
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
    private ArrayList<Long> getAllMatchIds(SQLiteDatabase db, long playerId) {
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
     * Get the ids of the specified matches.
     *
     * @param db
     * @param playerId The id of the player.
     * @param startIndex
     * @param amount
     * @return List of match ids.
     */
    private ArrayList<Long> getMatchIds(SQLiteDatabase db, long playerId, long startIndex, int amount) {
        ArrayList<Long> matchIds = new ArrayList<>();

        try (Cursor c = db.query(true, DartLogContract.ScoreEntry.TABLE_NAME,
                new String[]{DartLogContract.ScoreEntry.COLUMN_NAME_MATCH_ID},
                String.format(Locale.getDefault(), "%s = '%d' AND %s < '%d'",
                        DartLogContract.ScoreEntry.COLUMN_NAME_PLAYER_ID, playerId,
                        DartLogContract.ScoreEntry.COLUMN_NAME_MATCH_ID, startIndex),
                null,null, null,
                String.format(Locale.getDefault(), "%s DESC",
                        DartLogContract.ScoreEntry._ID),
                "0," + String.valueOf(amount))) {
            while (c.moveToNext()) {
                matchIds.add(c.getLong(c.getColumnIndex(
                        DartLogContract.ScoreEntry.COLUMN_NAME_MATCH_ID)));
            }
        }

        return matchIds;
    }

    /**
     * Get the game type of a match
     *
     * @param matchId The id of the match.
     * @return The game type of the game (x01, random).
     */
    private String getGameType(SQLiteDatabase db, long matchId) {
        String gameType = "";

        try (Cursor c = db.query(true, DartLogContract.Match.TABLE_NAME,
                new String[]{DartLogContract.Match.COLUMN_NAME_GAME_TYPE},
                String.format(Locale.getDefault(), "%s = '%d'",
                        DartLogContract.Match._ID,
                        matchId),
                null, null, null, null, null)) {
            if (c.moveToFirst()) {
                gameType = c.getString(c.getColumnIndex(
                        DartLogContract.Match.COLUMN_NAME_GAME_TYPE));
                while (c.moveToNext()) {
                    /* If more than one row of data is found we have encountered an error. */
                   throw new Error();
                }
            }
        }
        return gameType;
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

    private long insertMatchEntry(SQLiteDatabase db, Game game, String gameType){
        ContentValues matchValues = new ContentValues();

        matchValues.put(DartLogContract.Match.COLUMN_NAME_DATE,
                game.getDate().getTimeInMillis());
        matchValues.put(DartLogContract.Match.COLUMN_NAME_GAME_TYPE, gameType);
        matchValues.put(DartLogContract.Match.COLUMN_NAME_WINNER_PLAYER_ID,
                getPlayerId(db, game.getWinner()));

        return db.insert(DartLogContract.Match.TABLE_NAME, null, matchValues);
    }

    private long insertX01Entry(SQLiteDatabase db, X01 game, long matchId) {
        ContentValues matchValues = new ContentValues();
        matchValues.put(DartLogContract.X01Entry.COLUMN_NAME_X, game.getX());
        matchValues.put(DartLogContract.X01Entry.COLUMN_NAME_MATCH_ID, matchId);
        matchValues.put(DartLogContract.X01Entry.COLUMN_NAME_DOUBLE_OUT,
                game.getDoubleOutAttempts());
        return db.insert(DartLogContract.X01Entry.TABLE_NAME, null, matchValues);
    }

    private long insertRandomEntry(SQLiteDatabase db, Random game, long matchId) {
        ContentValues matchValues = new ContentValues();
        matchValues.put(DartLogContract.RandomEntry.COLUMN_NAME_MATCH_ID, matchId);
        matchValues.put(DartLogContract.RandomEntry.COLUMN_NAME_TURNS, game.getNrOfTurns());
        return db.insert(DartLogContract.RandomEntry.TABLE_NAME, null, matchValues);
    }

    private Cursor getX01MatchEntry(SQLiteDatabase db, long matchId) {
        String sql = "SELECT x.double_out, x.x, m.date, p.name as winner" +
                "     FROM x01 x" +
                "          join \"match\" m" +
                "               on m._ID = x.match_id" +
                "          join player p" +
                "               on p._ID = m.winner_id" +
                "     WHERE x.match_id = ?;";

        return db.rawQuery(sql, new String[]{String.valueOf(matchId)});
    }


    private Cursor getRandomMatchEntry(SQLiteDatabase db, long matchId) {
        String sql = "SELECT r.turns, m.date, p.name as winner" +
                "     FROM random r" +
                "          join \"match\" m" +
                "               on m._ID = r.match_id" +
                "          join player p" +
                "               on p._ID = m.winner_id" +
                "     WHERE r.match_id = ?;";

        return db.rawQuery(sql, new String[]{String.valueOf(matchId)});
    }

    private void initializePlayers(SQLiteDatabase db) {
        ArrayList<String> playersNames = new ArrayList<>();

        playersNames.add("Razmus");
        playersNames.add("Filip");
        playersNames.add("Fredrik");
        playersNames.add("Stefan");
        playersNames.add("Maria");

        for (String name : playersNames) {
            addPlayer(name, db);
        }
    }

    public boolean playerExist(String playerName) {
        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cc = db.query(DartLogContract.PlayerEntry.TABLE_NAME,
                new String[]{DartLogContract.PlayerEntry._ID},
                String.format("%s = '%s'", DartLogContract.PlayerEntry.COLUMN_NAME_PLAYER_NAME,
                        playerName), null, null, null, null)) {
            if (cc.getCount() == 1)
                return true;
        }
        catch(android.database.sqlite.SQLiteException e){
            return false;
        }
        return false;
    }
}
