package com.syncproxy.syncclient;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.FileObserver;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class SQLiteConnector extends BaseConnector {
    private static final String TAG = "SQLiteConnector";
    private FileObserver logFileObserver;
    private String journalPath;

    public SQLiteConnector(SyncClient sc, String dbName) {
        super(sc, dbName);
        this.Name = "SQLite";
        Log.d(TAG, "Connector loaded (dbName:" + dbName + ")");
    }

    SyncProxySQLiteOpenHelper getOpenHelper() {
        return new SyncProxySQLiteOpenHelper(syncClient.getApplicationContext(), dbName, 1);
    }

    SQLiteDatabase getDB() {
        return getOpenHelper().getWritableDatabase();
    }

    /*    public String readFileAsString(String filePath)
            throws java.io.IOException {
        StringBuilder fileData = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        char[] buf = new char[1024];
        int numRead = 0;
        while ((numRead = reader.read(buf)) != -1) {
            fileData.append(buf, 0, numRead);
        }
        reader.close();
        return fileData.toString();
    }*/
/*
    public void readData(String filePath){

        FileInputStream fis = null;

        try {
            fis = new FileInputStream(filePath);

            System.out.println("Total file size to read (in bytes) : " + fis.available());
            int content;
            while ((content = fis.read()) != -1) {
                System.out.print((char) content);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }*/

    ///////////////////////
    // Utility functions //
    ///////////////////////
    private JSONArray cursorKeysToJSON(Cursor crs) throws JSONException {
        // Serialize all key values (supposed to be in column 0) to a JSON array.
        JSONArray arr = new JSONArray();
        if (crs.getColumnCount() == 0) {
            Log.d(TAG, "PAS DE COLONNE");
            return arr;
        }
        crs.moveToFirst();
        while (!crs.isAfterLast()) {
            switch (crs.getType(0)) {
                case Cursor.FIELD_TYPE_BLOB:
                    arr.put(crs.getBlob(0).toString());
                    break;
                case Cursor.FIELD_TYPE_FLOAT:
                    arr.put(crs.getDouble(0));
                    break;
                case Cursor.FIELD_TYPE_INTEGER:
                    arr.put(crs.getLong(0));
                    break;
                case Cursor.FIELD_TYPE_NULL:
                    arr.put(null);
                    break;
                case Cursor.FIELD_TYPE_STRING:
                    arr.put(crs.getString(0));
                    break;
            }
            if (!crs.moveToNext())
                break;
        }
        return arr;
    }

    private JSONArray cursorToJSON(Cursor crs) throws JSONException {
        JSONArray arr = new JSONArray();
        if (crs.getColumnCount() == 0)
            return arr;
        crs.moveToFirst();
        while (!crs.isAfterLast()) {
            int nColumns = crs.getColumnCount();
            JSONObject row = new JSONObject();
            for (int i = 0; i < nColumns; i++) {
                String colName = crs.getColumnName(i);
                if (colName != null) {
                    String val = "";
                    switch (crs.getType(i)) {
                        case Cursor.FIELD_TYPE_BLOB:
                            row.put(colName, crs.getBlob(i).toString());
                            break;
                        case Cursor.FIELD_TYPE_FLOAT:
                            row.put(colName, crs.getDouble(i));
                            break;
                        case Cursor.FIELD_TYPE_INTEGER:
                            row.put(colName, crs.getLong(i));
                            break;
                        case Cursor.FIELD_TYPE_NULL:
                            row.put(colName, null);
                            break;
                        case Cursor.FIELD_TYPE_STRING:
                            row.put(colName, crs.getString(i));
                            break;
                    }
                }
            }
            arr.put(row);
            if (!crs.moveToNext())
                break;
        }
        return arr;
    }

    /////////////////////////////////
    // Schema extraction functions //
    /////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Local DB changes detection using _upsXXX and _delXXX tables populated by triggers on synched tables //
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void startChangesDetection() {
        // Start an observer on SQLite log file, to detect any change in the database.
        // Use a SQLiteHelper to create or update structures for change detection, and retrieve db file path
        if (logFileObserver != null)
            return;
        SQLiteDatabase db = getDB();
        journalPath = db.getPath();
        db.close();
        logFileObserver = new FileObserver(journalPath) {
            @Override
            public void onEvent(int event, String path) {
                switch (event) {
                    case FileObserver.CREATE:
                    case FileObserver.MODIFY:
                        Log.d(TAG, "SQLite log file CREATED OR MODIFIED");
                        if (changesDetectionEnabled)
                            syncClient.onClientChanges();
//                            syncClient.startSync(SyncClient.SyncType.ClientReactiveSync);
                        else
                            Log.d(TAG, "Log detection disabled");
                        break;
                    default:
                        // just ignore
                        break;
                }
/*                try{
                    String fileContent = readFileAsString(journalPath);
                    Log.d("FILEOBSERVER_EVENT", fileContent);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
*/
                //readData(journalPath);
            }
        };
        logFileObserver.startWatching(); // The FileObserver starts watching
        Log.d(TAG, "Observation of log file " + journalPath + " started");
    }

    @Override
    public void stopChangesDetection() {
        if (logFileObserver != null) {
            logFileObserver.stopWatching();
            logFileObserver = null;
            Log.d(TAG, "Observation of log file " + journalPath + " stopped");
        }
    }

    private String getGlobalModifTableName() {
        return "prox" + syncClient.getParam("proxyId").toString().substring(0, 5) + "_ModifiedTables";
    }

    private String getUpsTableName(String tableName) {
        return "prox" + syncClient.getParam("proxyId").toString().substring(0, 5) + "_ups" + tableName;
    }

    private String getDelTableName(String tableName) {
        return "prox" + syncClient.getParam("proxyId").toString().substring(0, 5) + "_del" + tableName;
    }

    private String getInsTriggerName(String tableName) {
        return "prox" + syncClient.getParam("proxyId").toString().substring(0, 5) + "_trg" + tableName + "Ins";
    }

    private String getUpdTriggerName(String tableName) {
        return "prox" + syncClient.getParam("proxyId").toString().substring(0, 5) + "_trg" + tableName + "Upd";
    }

    private String getDelTriggerName(String tableName) {
        return "prox" + syncClient.getParam("proxyId").toString().substring(0, 5) + "_trg" + tableName + "Del";
    }

    private void createGlobalModifTable(SQLiteDatabase db) throws Exception {
        // ModifiedTables table will dynamically receive the names of tables that have been modified
        String globalModifTable = getGlobalModifTableName();
        String sql = "CREATE TABLE IF NOT EXISTS \"" + globalModifTable + "\" (\"tableName\" VARCHAR(255) PRIMARY KEY)";
        Log.d(TAG, sql);
        db.execSQL(sql);
/*        sql = "CREATE INDEX IF NOT EXISTS \"" + globalModifTable + "Name\" ON \"" + globalModifTable + "\" (\"tableName\")";
        Log.d(TAG, sql);
        db.execSQL(sql);*/
    }

    private void createUpsTable(SQLiteDatabase db, Table table) throws Exception {
        Column pkCol = table.findColumn(table.PK);
        if (pkCol == null)
            throw new Exception("createUpsTable for table " + table + ": Error, table has no key");
        String upsTableName = getUpsTableName(table.Name);
        String sql = "CREATE TABLE IF NOT EXISTS \"" + upsTableName + "\" (\"key\" " + pkCol.Type + " PRIMARY KEY, \"_isNew\" INT)";
        Log.d(TAG, sql);
        db.execSQL(sql);
/*        sql = "CREATE INDEX IF NOT EXISTS \"" + upsTableName + pkCol.Name + "\" ON \"" + upsTableName + "\" (\"key\")";
        Log.d(TAG, sql);
        db.execSQL(sql);
*/
        // If source table was not empty initially, mark all existing records for upsert
        sql = "INSERT OR IGNORE INTO \"" + upsTableName + "\" (\"key\") SELECT \"" + table.PK + "\" FROM \"" + table.Name + "\"";
        Log.d(TAG, sql);
        db.execSQL(sql);
    }

    private void createDelTable(SQLiteDatabase db, Table table) throws Exception {
        Column pkCol = table.findColumn(table.PK);
        if (pkCol == null)
            throw new Exception("createDelTable for table " + table + ": Error, table has no key");
        String delTableName = getDelTableName(table.Name);
        String sql = "CREATE TABLE IF NOT EXISTS \"" + delTableName + "\" (\"key\" " + pkCol.Type + " PRIMARY KEY)";
        Log.d(TAG, sql);
        db.execSQL(sql);
    }

    private void createUpsTrigger(SQLiteDatabase db, Table table) {
        String upsTableName = getUpsTableName(table.Name);
        String globalModifTableName = getGlobalModifTableName();
        String triggerDef =
                " ON \"" + table.Name + "\" FOR EACH ROW BEGIN INSERT OR IGNORE INTO \"" + upsTableName + "\" (\"key\", \"_isNew\") VALUES (NEW.\"" + table.PK + "\", isNewVal);" +
                        "INSERT OR IGNORE INTO \"" + globalModifTableName + "\" (\"tableName\") VALUES ('" + table.Name + "');" +
                        "END";
        String insert = "CREATE TRIGGER IF NOT EXISTS \"" + getInsTriggerName(table.Name) + "\" AFTER INSERT " + triggerDef.replaceAll("isNewVal", "1");
        String update = "CREATE TRIGGER IF NOT EXISTS \"" + getUpdTriggerName(table.Name) + "\" AFTER UPDATE " + triggerDef.replaceAll("isNewVal", "0");
        Log.d(TAG, insert);
        db.execSQL(insert);
        Log.d(TAG, update);
        db.execSQL(update);
    }

    private void createDelTrigger(SQLiteDatabase db, Table table) {
        String delTableName = getDelTableName(table.Name);
        String globalModifTableName = getGlobalModifTableName();
        String sql = "CREATE TRIGGER IF NOT EXISTS \"" + getDelTriggerName(table.Name) + "\" AFTER DELETE ON \"" + table.Name + "\" FOR EACH ROW BEGIN " +
                "INSERT OR IGNORE INTO \"" + delTableName + "\" (\"key\") VALUES (OLD.\"" + table.PK + "\");" +
                "INSERT OR IGNORE INTO \"" + globalModifTableName + "\" (\"tableName\") VALUES ('" + table.Name + "');" +
                "END";
        Log.d(TAG, sql);
        db.execSQL(sql);
    }

    //////////////////////
    // Schema functions //
    //////////////////////
    @Override
    protected String getColDef(Table table, String colName) {
        Column col = table.findColumn(colName);
        if (col == null)
            return "";
        String primaryKey = "", size, nullable = "";
        Object defaultVal = "";
        if (col.Name.equals(table.PK)) {
            primaryKey = " PRIMARY KEY";
            if (table.AutoIncrement)
                primaryKey += " AUTOINCREMENT";
        }
        if (!col.Nullable && col.hasDefaultValue())
            nullable = " NOT NULL";
        else
            nullable = "";
        if (col.Size > 0)
            size = "(" + col.Size + ")";
        else
            size = "";
        if (col.hasDefaultValue())
            defaultVal = " DEFAULT " + col.Default.toString();
        else
            defaultVal = "";
        return col.Type + size + nullable + defaultVal + primaryKey;
    }

    // Retrieve key column directly from local db strucure (if no tableName is given, get all tables)
    public void getKeyNamesFromDatabase(String tableName) {

    }

    public void upgradeDatabase(JSONObject schema) throws Exception {
        String currVersion = getDBVersion();
        SyncClient.log(TAG, "currVersion=" + currVersion + " newSchema.version=" + schema.getInt("version"), SyncClient.LogLevel.info);
        Boolean firstUpgrade = false;
        if (currVersion.toString().equals("0")) {
            firstUpgrade = true;
            currVersion = "1";        // first upgrade: force version to 1, whatever newSchema version
        }
        if (!firstUpgrade && (schema.getString("version").compareTo(currVersion) < 0))
            return;        // nothing to do
        SyncClient.log(TAG, "upgradeDatabase to version=" + schema.getString("version"), SyncClient.LogLevel.info);
        JSONArray tables = schema.getJSONArray("Tables");

        SQLiteDatabase db = getDB();

        SyncClient.log(TAG, "db = " + db.getPath(), SyncClient.LogLevel.info);

        suspendChangesDetection();
        createGlobalModifTable(db);
        for (int t = 0; t < tables.length(); t++) {
            Table table = new Table((JSONObject) tables.get(t));
            if ((table.PK == null) || table.PK.equals("")) {
                SyncClient.log(TAG, "Table " + table.Name + " was not created because it has no primary key", SyncClient.LogLevel.error);
                continue;
            }
            String sql = "CREATE TABLE IF NOT EXISTS \"" + table.Name + "\" (\"" + table.PK + "\" " + getColDef(table, table.PK) + ")";
            SyncClient.log(TAG, sql, SyncClient.LogLevel.info);
            db.execSQL(sql);
            // Create columns
            ListIterator itCol = table.Columns.listIterator();
            while (itCol.hasNext()) {
                Column col = (Column) itCol.next();
                if (col.Name.equals(table.PK))
                    continue;
                // Try to add the column if it does not exist yet.
                String sqlAddCol = "ALTER TABLE \"" + table.Name + "\" ADD \"" + col.Name + "\" " + getColDef(table, col.Name);
                SyncClient.log(TAG, sqlAddCol, SyncClient.LogLevel.info);
                try {
                    db.execSQL(sqlAddCol);
                } catch (SQLiteException e) {
                }
            }
            // Create chg_Table and del_Table (to flag changes and deletes)
            createUpsTable(db, table);
            createUpsTrigger(db, table);
            createDelTable(db, table);
            createDelTrigger(db, table);
        }
        db.close();
        resumeChangesDetection();
    }

    /////////////////////////
    // Sync data to server //
    /////////////////////////
    @Override
    protected void flushDeletedUpserts(String tableName) throws JSONException {
        // Flush from _ups and _del tables all records that are both new and deleted.
        String upsTable = getUpsTableName(tableName);
        String delTable = getDelTableName(tableName);
        SQLiteDatabase db = getDB();
        String sqlSelectKeysToDelete = "SELECT \"" + upsTable + "\".\"key\" FROM \"" + delTable + "\" INNER JOIN \"" + upsTable + "\" ON \"" + delTable + "\".\"key\"=\"" + upsTable + "\".\"key\" WHERE \"" + upsTable + "\".\"_isNew\"=1";
        SyncClient.log(TAG, sqlSelectKeysToDelete, SyncClient.LogLevel.info);
        Cursor csrKeysToDelete = db.rawQuery(sqlSelectKeysToDelete, null);
        JSONArray keysToDelete = cursorToJSON(csrKeysToDelete);
        csrKeysToDelete.close();

        if ( keysToDelete.length() > 0 ) {
            String sqlDeleteFromDelTable = "DELETE FROM \"" + delTable + "\" WHERE \"key\" IN (" + keysToDelete.toString().replace("{", "").replace("}", "") + ")";
            SyncClient.log(TAG, sqlSelectKeysToDelete, SyncClient.LogLevel.info);
            db.execSQL(sqlDeleteFromDelTable);

            String sqlDeleteFromUpsTable = "DELETE FROM \"" + upsTable + "\" WHERE \"key\" IN (" + keysToDelete.toString().replace("{", "").replace("}", "") + ")";
            SyncClient.log(TAG, sqlDeleteFromUpsTable, SyncClient.LogLevel.info);
            db.execSQL(sqlDeleteFromUpsTable);

            SyncClient.log(TAG, sqlSelectKeysToDelete, SyncClient.LogLevel.info);
        }
        db.close();
    }

    @Override
    public List<String> getModifiedTables() {
        List<String> result = new ArrayList<>();
        SQLiteDatabase db = getDB();
        Cursor data = db.rawQuery("SELECT \"tableName\" FROM \"" + getGlobalModifTableName() + "\"", null);
        data.moveToFirst();
        while (!data.isAfterLast()) {
            result.add(data.getString(0));
            if (!data.moveToNext())
                break;
        }
        data.close();
        db.close();
        return result;
    }

    public JSONArray getDeletes(String tableName) throws JSONException {
        SQLiteDatabase db = getDB();
        String query = "SELECT \"key\" FROM " + getDelTableName(tableName);
        SyncClient.log(TAG, query, SyncClient.LogLevel.info);
        Cursor data = db.rawQuery(query, null);
        JSONArray result = cursorKeysToJSON(data);
        data.close();
        db.close();
        return result;
    }

    public JSONArray getUpserts(String tableName) throws JSONException {
        // Retrieve columns to sync
        Table table = syncClient.schema.findTableFromName(tableName);
        if (table == null)
            return null;
        String cols = table.getSyncColumnsString();
        SQLiteDatabase db = getDB();
        String upsTable = getUpsTableName(tableName);
        cols += ",\"" + upsTable + "\".\"_isNew\"";
        String chgKey = "\"" + upsTable + "\".\"key\"";
        String query = "SELECT " + cols + " FROM \"" + tableName + "\" INNER JOIN " + upsTable + " ON \"" + tableName + "\".\"" + table.PK + "\"=" + chgKey;
        SyncClient.log(TAG, query, SyncClient.LogLevel.info);
        Cursor data = db.rawQuery(query, null);
        JSONArray result = cursorToJSON(data);
        data.close();
        db.close();
        return result;
    }

    protected void resetKeys(String upsTableName, JSONArray keys) throws JSONException {
        if (keys.length() == 0)
            return;
        SQLiteDatabase db = getDB();
        String sKeys = keys.toString();     // results in "[a,b,c...]"
        String sql = "DELETE FROM \"" + upsTableName + "\" WHERE \"key\" IN (" + sKeys.substring(1, sKeys.length() - 1) + ")";       // remove opening and closing [] from sKeys
        SyncClient.log(TAG, sql, SyncClient.LogLevel.info);
        db.execSQL(sql);
    }

    @Override
    protected void resetDeletes(String tableName, JSONArray keys) throws JSONException {
        resetKeys(getDelTableName(tableName), keys);
    }

    @Override
    protected void resetUpserts(String tableName, JSONArray keys) throws JSONException {
        resetKeys(getUpsTableName(tableName), keys);
    }

    @Override
    // Remove tableName from ModifiedTables if _ups<tableName> and _del<tableName> are empty (no more client-side pending changes in that table).
    protected void recomputeModifiedTables(List<String> tableNames) {
        if ( (tableNames == null) || tableNames.isEmpty() )
            return;
        Iterator<String> itTableNames = tableNames.iterator();
        List<String> unmodifiedTables = new ArrayList<String>();
        SQLiteDatabase db = getDB();
        while (itTableNames.hasNext()) {
            String tableName = itTableNames.next();
            Boolean hasChanges = false;
            Cursor dataUps = db.rawQuery("SELECT COUNT(*) FROM \"" + getDelTableName(tableName) + "\"", null);
            dataUps.moveToFirst();
            if (dataUps.getInt(0) > 0)
                hasChanges = true;
            Cursor dataDel = db.rawQuery("SELECT COUNT(*) FROM \"" + getUpsTableName(tableName) + "\"", null);
            dataDel.moveToFirst();
            if (dataDel.getInt(0) > 0)
                hasChanges = true;
            if (!hasChanges)
                unmodifiedTables.add(tableName);
        }
        if ( !unmodifiedTables.isEmpty() ) {
            String sql = "DELETE FROM \"" + getGlobalModifTableName() + "\" WHERE \"tableName\" IN (" + unmodifiedTables.toString().replaceAll("\\[", "'").replaceAll("\\]", "'") + ")";
            SyncClient.log(TAG, sql, SyncClient.LogLevel.info);
            db.execSQL(sql);
        }
    }

    ///////////////////////////
    // Sync data from server //
    ///////////////////////////
    @Override
    protected void handleDeletes(Table table, JSONArray deletes) throws JSONException {
        if (deletes == null)
            return;
        // Remove opening and closing braces and brackets from json string
        String sKeys = deletes.toString().replace("{", "").replace("}", "").replace("[", "").replace("]", "");
        if (sKeys == "")
            return;     // nothing to do
        String sql = "DELETE FROM \"" + table.Name + "\" WHERE \"" + table.PK + "\" IN (" + sKeys + ")";
        SyncClient.log(TAG, sql, SyncClient.LogLevel.info);
        SQLiteDatabase db = getDB();
        db.execSQL(sql);
        // Remove all entries that have been created in _del table by AFTER DELETE trigger during server sync
        resetDeletes(table.Name, deletes);
    }

    @Override
    protected void handleUpserts(Table table, JSONArray upserts) throws JSONException {
        if (upserts == null)
            return;
        List<Column> syncCols = table.getSyncColumns();
        if ( syncCols == null )
            return;
        SQLiteDatabase db = getDB();
        Boolean textPK = false;
        if (Arrays.asList(new String[]{"TEXT", "VARCHAR", "NVARCHAR", "NVARCHAR2"}).contains(table.findColumn(table.PK).Type.toUpperCase()))
            textPK = true;
        JSONArray upsertsKeys = new JSONArray();

        for (int r = 0; r < upserts.length(); r++) {
            JSONObject row;
            row = upserts.getJSONObject(r);
            String sRow = row.toString();
            sRow = sRow.substring(1, sRow.length() - 1);        // remove heading and trailing braces { and }
            SyncClient.log(TAG, "sRow=" + sRow, SyncClient.LogLevel.info);

            // Get columns names and values (for SQL INSERT) and keys=values (for SQL UPDATE)
            String columnNames = "";
            String values = new String(sRow);   // will get values as SQL string (for INSERT)
            String keysValues = new String(sRow);       // will get keys/values as SQL string (for UPDATE)

            Iterator<Column> itSyncCols = syncCols.iterator();
            while (itSyncCols.hasNext()) {
                Column col = itSyncCols.next();
                if (row.has(col.Name)) {
                    if (!columnNames.isEmpty())
                        columnNames += ",";
                    columnNames += "\"" + col.Name + "\"";
                    keysValues = keysValues.replaceFirst("\"" + col.Name + "\":", "\"" + col.Name + "\"=");
                }
                values = values.replaceFirst("\"" + col.Name + "\":", "");       // remove column name from JSON object to get a list of values.
            }
            String pkColVal = "\"" + table.PK + "\"=";
            if ( textPK )
                pkColVal += "'";
            pkColVal += row.get(table.PK);
            if ( textPK )
                pkColVal += "'";

            // UPDATE if record already exists, then INSERT OR IGNORE
            String sqlUpdate = "UPDATE \"" + table.Name + "\" SET " + keysValues + " WHERE " + pkColVal;
            SyncClient.log(TAG, sqlUpdate, SyncClient.LogLevel.info);
            db.execSQL(sqlUpdate);

            // INSERT if record doesn't exist
            itSyncCols = syncCols.iterator();
            String sqlInsert = "INSERT OR IGNORE INTO \"" + table.Name + "\" (" + columnNames + ") VALUES (" + values + ")";
            SyncClient.log(TAG, sqlInsert, SyncClient.LogLevel.info);
                db.execSQL(sqlInsert);

            // Add key for removal from _ups table
            upsertsKeys.put(row.get(table.PK));
        }
        // Remove all entries that have been created in _ups table by AFTER INSERT/UPDATE triggers during server sync
        resetUpserts(table.Name, upsertsKeys);
    }
}