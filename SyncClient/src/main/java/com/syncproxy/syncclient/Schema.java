package com.syncproxy.syncclient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class Schema{
    List<Table> Tables = new ArrayList<Table>();
    int version = 0;

    public Schema(JSONObject schema) throws JSONException {
        fromJSON(schema);
    }

    public void fromJSON(JSONObject json) throws JSONException {
        Tables.clear();
        version = json.getInt("version");
        JSONArray jsonArray = json.getJSONArray("Tables");
        for (int i=0; i < jsonArray.length(); i++) {
            Tables.add(new Table((JSONObject)jsonArray.get(i)));
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("version", version);
        JSONArray jsonTables = new JSONArray();
        ListIterator<Table> liT = Tables.listIterator();
        while ( liT.hasNext() ){
            Table table = liT.next();
            jsonTables.put(table.toJSON());
        }
        json.put("Tables", jsonTables);
        return json;
    }

    public Table findTableFromName(String tableName){
        Iterator<Table> itT = Tables.iterator();
        while ( itT.hasNext()){
            Table table = itT.next();
            if ( table.Name.equals(tableName))
                return table;
        }
        return null;
    }
}
