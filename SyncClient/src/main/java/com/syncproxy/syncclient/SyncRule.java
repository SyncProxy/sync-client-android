package com.syncproxy.syncclient;

import org.json.JSONException;
import org.json.JSONObject;

public class SyncRule{
    public String tableName;
    public short clientSync;       // 0:none / 1:sync / 2:reactive sync
    public short dbSync;       // 0:none / 1:sync / 2:reactive sync

    public SyncRule(JSONObject syncRule) throws JSONException {
        fromJSON(syncRule);
    }

    public void fromJSON(JSONObject json) throws JSONException {
        // A sync rule is: {"tableName":"<tableName">, "clientSync":0/1/2, "dbSync":0/1/2, ...(ignored)}
        tableName = json.getString("tableName");
        clientSync = (short)json.getInt("clientSync");
        dbSync = (short)json.getInt("dbSync");
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("tableName", tableName);
        json.put("clientSync", clientSync);
        json.put("dbSync", dbSync);
        return json;
    }
}
