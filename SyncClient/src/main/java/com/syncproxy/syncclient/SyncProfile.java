package com.syncproxy.syncclient;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class SyncProfile{
    List<SyncRule> syncRules = new ArrayList<SyncRule>();

    public SyncProfile(JSONObject syncRules) throws JSONException {
        fromJSON(syncRules);
    }

    public void fromJSON(JSONObject json) throws JSONException {
        syncRules.clear();
        for(int i = 0; i<json.names().length(); i++){
            syncRules.add(new SyncRule(json.getJSONObject(json.names().getString(i))));
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        JSONObject jsonSyncRules = new JSONObject();
        ListIterator<SyncRule> liSR = syncRules.listIterator();
        while ( liSR.hasNext() ){
            SyncRule syncRule = liSR.next();
            jsonSyncRules.put(syncRule.tableName, syncRule.toJSON());
        }
        json.put("syncRules", jsonSyncRules);
        return json;
    }

}

