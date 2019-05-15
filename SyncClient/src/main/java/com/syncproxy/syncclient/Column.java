package com.syncproxy.syncclient;

import org.json.JSONException;
import org.json.JSONObject;

public class Column{
    String Name;
    String Type;
    int Size = 0;
    Boolean Nullable = true;
    Object Default = null;
    Boolean Sync = true;

    public Column (JSONObject json) throws JSONException {
        fromJSON(json);
    }

    public Boolean hasDefaultValue(){
        return (Default != null) && !Default.equals("") && !Default.toString().equals("null");
    }

    public void fromJSON(JSONObject json) throws JSONException {
        Name = json.getString("Name");
        Type = json.getString("Type");
        if ( Type.equals("INT") )
            Type = "INTEGER";       // SQLite has trouble recognizing short "INT" type instead of "INTEGER" for PK with AutoIncrement !
        if ( !json.isNull("Size"))
            Size = json.getInt("Size");
        if ( !json.isNull("Nullable") )
            Nullable = json.getBoolean("Nullable");
        Default = json.get("Default");
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("Name", Name);
        json.put("Type", Type);
        json.put("Size", Size);
        json.put("Nullable", Nullable);
        json.put("Default", Default);
        return json;
    }
}
