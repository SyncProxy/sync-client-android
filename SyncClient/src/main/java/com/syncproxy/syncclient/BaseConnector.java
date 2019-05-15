package com.syncproxy.syncclient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public abstract class BaseConnector {
    private static final String TAG = "BaseConnector";
    public String Name;
    protected String dbName;
    protected SyncClient syncClient;
    protected Boolean changesDetectionEnabled = true;


    public BaseConnector(SyncClient sc){
        syncClient = sc;
    }

    public BaseConnector(SyncClient sc, String dbName) {
        this.dbName = dbName;
        syncClient = sc;
    }

    private String getItem(String key){
        return syncClient.getItem(key);
    }

    private void setItem(String key, String value){
        syncClient.setItem(key, value);
    }

    private void removeItem(String key){
    }


    //////////////////////
    // Schema functions //
    //////////////////////
    protected String getColDef(Table table, String colName){
        return null;
    }
/*
    public Table jsonToTable(JSONObject json) throws JSONException {
        Table table = new Table();
        table.Name = json.getString("Name");
        table.PK = json.getString("PK");
        JSONArray jsonArray = json.getJSONArray("Columns");
        for (int i=0; i < jsonArray.length(); i++) {
            table.Columns.add(jsonToColumn((JSONObject)jsonArray.get(i)));
        }
        return table;
    }

    public Column jsonToColumn(JSONObject json) throws JSONException {
        Column column = new Column();
        column.Name = json.getString("Name");
        column.Type = json.getString("Type");
        column.Size = json.getInt("Size");
        column.Nullable = json.getBoolean("Nullable");
        column.Default = json.get("Default");
        return column;
    }
*/
    public String getDBVersion(){
        String v = getItem(this.dbName + ".dbVersion");
        if ( (v == null) || (v.equals("")) )
            return "0";
        return v;
    }

    public void setDBVersion(String version){
        this.setItem(this.dbName + ".dbVersion", version);
    }

    public void upgradeDatabase(JSONObject schema) throws Exception {
    };

    private String getKeyName(String tableName){
        return null;
    }

    /////////////////////////
    // Sync data to server //
    /////////////////////////
    protected void flushDeletedUpserts(String tableName) throws JSONException {
    }

    public void startChangesDetection(){
    }

    public void stopChangesDetection(){
    }

    public void suspendChangesDetection() {
        changesDetectionEnabled = false;
        SyncClient.log(TAG, "Changes detection suspended", SyncClient.LogLevel.info);
    }

    public void resumeChangesDetection() {
        changesDetectionEnabled = true;
        SyncClient.log(TAG, "Changes detection resumed", SyncClient.LogLevel.info);
    }

    public List<String> getModifiedTables(){
        return null;
    }

    public JSONArray getDeletes(String tableName) throws JSONException {
        return null;
    }

    public JSONArray getUpserts(String tableName) throws JSONException {
        return null;
    }

    protected void resetDeletes(String tableName, JSONArray keys) throws JSONException {
    }

    protected void resetUpserts(String tableName, JSONArray keys) throws JSONException {
    }

    // Remove tableName from ModifiedTables if _ups<tableName> and _del<tableName> are empty (no more client-side pending changes in that table).
    protected void recomputeModifiedTables(List<String> tableNames){
    }

    protected List<Object> getAllDeletes(List<String> tables) {
        List<Object> result = new ArrayList<>();
        return result;
    }

    protected List<Object> getAllUpserts(List<String> tables) {
        List<Object> result = new ArrayList<>();
        return result;
    }


    ///////////////////////////
    // Sync data from server //
    ///////////////////////////
    protected void handleUpserts(Table table, JSONArray upserts) throws JSONException {
    }

    protected void handleDeletes(Table table, JSONArray upserts) throws JSONException {
    }
}