package com.syncproxy.syncclient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Changes{
    public JSONObject upserts;
    public JSONObject deletes;

    public Changes(JSONObject ups, JSONObject del){
        upserts = ups;
        deletes = del;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject result = new JSONObject();
        result.put("Upserts", upserts);
        result.put("Deletes", deletes);
        return result;
    }

    public JSONArray getDeletesKeys(String tableName) throws JSONException {
        if (  !upserts.has(tableName) )
            return null;
        return deletes.getJSONArray(tableName);
    }

    public JSONArray getUpsertsKeys(String tableName, String keyName) throws JSONException {
        if (  !upserts.has(tableName) )
            return null;
        JSONArray result = new JSONArray();
        JSONArray records = upserts.getJSONArray(tableName);
        for ( short r = 0; r < records.length(); r++){
            JSONObject current = (JSONObject)records.get(r);
            if ( current.has(keyName) )
                result.put(current.get(keyName));
        }
        return result;
    }

    public List<String> getDeletesTables(){
        List<String> result = new ArrayList<String>();
        Iterator<String> deletesTables = deletes.keys();
        while(deletesTables.hasNext()){
            String tableName = deletesTables.next();
            if (!result.contains(tableName));
                result.add(tableName);
        }
        return result;
    }

    public List<String> getUpsertsTables(){
        List<String> result = new ArrayList<String>();
        Iterator<String> upsertsTables = upserts.keys();
        while(upsertsTables.hasNext()){
            String tableName = upsertsTables.next();
            if (!result.contains(tableName));
            result.add(tableName);
        }
        return result;
    }
}
