package com.syncproxy.syncclient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class Table {
    String Name;
    String PK = null;
    Boolean Sync = false;
    Boolean AutoIncrement = false;
    List<Column> Columns = new ArrayList<Column>();
    final String TAG = "Table";

    public Table(JSONObject json) throws JSONException {
        fromJSON(json);
    }

    public void fromJSON(JSONObject json) throws JSONException {
        Columns.clear();
        Name = json.getString("Name");
        PK = json.getString("PK");
        if ( !json.isNull("Sync") )
            Sync = json.getBoolean("Sync");
        if ( !json.isNull("AutoIncrement") ) {
            if ( json.get("AutoIncrement").toString().equals("1") )
                AutoIncrement = true;
            else
                AutoIncrement = false;
        }
        JSONArray jsonArray = json.getJSONArray("Columns");
        for (int i=0; i < jsonArray.length(); i++) {
            Columns.add(new Column((JSONObject)jsonArray.get(i)));
        }
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("Name", Name);
        json.put("PK", PK);
        json.put("Sync", Sync);
        json.put("AutoIncrement", AutoIncrement);
        JSONArray jsonColumns = new JSONArray();
        ListIterator<Column> liC = Columns.listIterator();
        while ( liC.hasNext() ){
            Column col = liC.next();
            jsonColumns.put(col.toJSON());
        }
        json.put("Columns", jsonColumns);
        return json;
    }

    public Column findColumn(String colName){
        ListIterator itCol = Columns.listIterator();
        while(itCol.hasNext()) {
            Column col = (Column) itCol.next();
            if (col.Name.equals(colName))
                return col;
        }
        return null;
    }

    public String getSyncColumnsString(){
        String result = "";
        ListIterator itCol = getSyncColumns().listIterator();
        while(itCol.hasNext()) {
            Column col = (Column) itCol.next();
            if ( !result.isEmpty() )
                result += ",";
            result += "\"" + Name + "\".\"" + col.Name + "\"";
        }
        return result;
    }

    public List<Column> getSyncColumns(){
        List<Column> result = new ArrayList<Column>();
        ListIterator itCol = Columns.listIterator();
        while(itCol.hasNext()) {
            Column col = (Column) itCol.next();
            if (col.Sync )
                result.add(col);
        }
        return result;
    }
}

